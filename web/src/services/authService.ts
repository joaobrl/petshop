import { apiFetch } from "./http";
import { API_BASE } from "./config";
import type { LoginResponse } from "../types/auth";
import type { Customer, CustomerRegistrationRequest } from "../types/customer";
import type { Role, AccountType } from "../types/staff";

// The JWT (petshop-commons' JwtService) carries sub=id plus email/name/cpf/
// phone/role/type as plain claims — decoding it client-side avoids a
// separate "whoami" call (there's no such REST endpoint in this backend).
export interface DecodedToken {
  id: string;
  email: string;
  name: string;
  cpf: string;
  phone: string;
  role: Role;
  type: AccountType;
  // "email_verified" claim (SDD-020). Only customers get it; undefined =
  // claim absent (staff, tokens issued before the change) = not blocked.
  emailVerified?: boolean;
  exp?: number; // seconds since epoch (JWT "exp" claim)
}

export function decodeToken(token: string): DecodedToken {
  const payload = token.split(".")[1];
  const json = JSON.parse(decodeURIComponent(escape(atob(payload.replace(/-/g, "+").replace(/_/g, "/")))));
  return {
    id: json.sub,
    email: json.email,
    name: json.name,
    cpf: json.cpf,
    phone: json.phone,
    role: json.role,
    type: json.type,
    emailVerified: typeof json.email_verified === "boolean" ? json.email_verified : undefined,
    exp: typeof json.exp === "number" ? json.exp : undefined,
  };
}

export function isExpired(decoded: DecodedToken): boolean {
  return decoded.exp !== undefined && decoded.exp * 1000 <= Date.now();
}

// Only internal paths: "/x" is fine, "//host" and "/\host" are not.
export function safeNext(next: string | null): string | null {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return null;
  return next;
}

export async function login(email: string, password: string): Promise<LoginResponse> {
  return apiFetch<LoginResponse>(API_BASE.customers, "/api/v1/auth/login", {
    method: "POST",
    body: { email, password },
    auth: false,
  });
}

// Customer with an explicit email_verified=false claim: checkout/booking
// answer 403 until the e-mail is confirmed (SDD-020).
export function isUnverifiedCustomer(user: DecodedToken | null): boolean {
  return user !== null && user.type === "CUSTOMER" && user.emailVerified === false;
}

export async function verifyEmail(token: string): Promise<void> {
  await apiFetch<void>(API_BASE.customers, "/api/v1/auth/verify-email", {
    method: "POST",
    body: { token },
    auth: false,
  });
}

// Backend always answers 202, whether the e-mail exists or not.
export async function resendVerification(email: string): Promise<void> {
  await apiFetch<void>(API_BASE.customers, "/api/v1/auth/resend-verification", {
    method: "POST",
    body: { email },
    auth: false,
  });
}

export async function changePassword(email: string, currentPassword: string, newPassword: string): Promise<void> {
  await apiFetch<void>(API_BASE.customers, "/api/v1/auth/change-password", {
    method: "PATCH",
    body: { email, currentPassword, newPassword },
  });
}

export async function registerCustomer(data: CustomerRegistrationRequest): Promise<Customer> {
  return apiFetch<Customer>(API_BASE.customers, "/api/v1/customers/register/customer", {
    method: "POST",
    body: data,
    auth: false,
  });
}
