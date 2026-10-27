// Shared fetch wrapper. Every backend module extends petshop-commons'
// BaseExceptionHandler, which returns RFC 7807 `application/problem+json`
// errors ({ type, title, status, detail, errors?: [{field, message}] }) —
// this parses that shape and throws an ApiError with a message fit to show
// the user directly.

export class ApiError extends Error {
  status: number;
  fieldErrors?: { field: string; message: string }[];

  constructor(status: number, message: string, fieldErrors?: { field: string; message: string }[]) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

const TOKEN_KEY = "petshop.token";

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  auth?: boolean; // attach Authorization header (default true)
}

export async function apiFetch<T>(baseUrl: string, path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, auth = true } = options;

  const headers: Record<string, string> = {};
  if (body !== undefined) headers["Content-Type"] = "application/json";
  if (auth) {
    const token = getToken();
    if (token) headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

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
