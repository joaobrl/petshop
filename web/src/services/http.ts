// Shared fetch wrapper. Every backend module extends petshop-commons'
// BaseExceptionHandler, which returns RFC 7807 `application/problem+json`
// errors ({ type, title, status, detail, errors?: [{field, message}] }) —
// this parses that shape and throws an ApiError with a message fit to show
// the user directly.

import { getAccessToken, hasSession, renewToken } from "./oidc";

export class ApiError extends Error {
  status: number;
  fieldErrors?: { field: string; message: string }[];

  constructor(status: number, message: string, fieldErrors?: { field: string; message: string }[]) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

// Emits "session-expired" when an authenticated request is still rejected with 401
// after one renewal attempt (SDD-015). AuthContext listens to it.
export const authEvents = new EventTarget();
export const SESSION_EXPIRED_EVENT = "session-expired";

function expireSession() {
  authEvents.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  auth?: boolean; // attach Authorization header (default true)
}

function send(baseUrl: string, path: string, method: string, body: unknown, token: string | null) {
  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers["Authorization"] = `Bearer ${token}`;
  return fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
}

export async function apiFetch<T>(baseUrl: string, path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, auth = true } = options;

  // Access token only in memory (oidc.ts); null = anonymous or expired.
  let token = auth ? getAccessToken() : null;
  if (auth && !token && hasSession()) {
    // Access token past its exp (e.g. tab was asleep): renew before calling.
    token = await renewToken();
    if (!token) {
      expireSession();
      throw new ApiError(401, "Sua sessão expirou, entre novamente.");
    }
  }
  let response = await send(baseUrl, path, method, body, token);

  // 401 on an authenticated call: one shared renewal via the Keycloak refresh
  // token / SSO, then retry once; still failing = session expired (SDD-015).
  if (response.status === 401 && auth && token) {
    const fresh = await renewToken();
    if (fresh) {
      response = await send(baseUrl, path, method, body, fresh);
    }
    if (!fresh || response.status === 401) {
      expireSession();
    }
  }

  if (response.status === 204) return undefined as T;

  const contentType = response.headers.get("content-type") ?? "";
  const isJson = contentType.includes("json");
  const payload = isJson ? await response.json() : await response.text();

  if (!response.ok) {
    if (isJson && payload && typeof payload === "object") {
      const problem = payload as { detail?: string; title?: string; errors?: { field: string; message: string }[] };
      throw new ApiError(
        response.status,
        problem.detail ?? problem.title ?? "Ocorreu um erro inesperado.",
        problem.errors,
      );
    }
    throw new ApiError(response.status, "Ocorreu um erro inesperado.");
  }

  return payload as T;
}
