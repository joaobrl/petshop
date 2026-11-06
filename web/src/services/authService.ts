import { apiFetch } from "./http";
import { API_BASE } from "./config";
import type { Customer, CustomerRegistrationRequest } from "../types/customer";
import type { Role, AccountType } from "../types/staff";

// Access token do Keycloak (SDD-024 F3): claims user_id (UUID do customer-management),
// email, name, cpf, phone, type, email_verified e o papel em realm_access.roles —
// decodificá-lo no cliente evita um "whoami" (não existe esse endpoint). Só leitura
// para a UI: quem valida o token são os serviços.
export interface DecodedToken {
  id: string;
  email: string;
  name: string;
  cpf: string;
  phone: string;
  role: Role | null;
  type: AccountType;
  // "email_verified" (SDD-020), vindo do Keycloak. undefined = claim ausente = não bloqueia.
  emailVerified?: boolean;
  exp?: number; // seconds since epoch (JWT "exp" claim)
}

// Mesma ordem de prioridade do petshop-commons (Role.values()).
const APP_ROLES: Role[] = ["ADMIN", "RECEPTIONIST", "GROOMER", "VETERINARIAN", "CUSTOMER"];

export function decodeToken(token: string): DecodedToken {
  const payload = token.split(".")[1];
  const json = JSON.parse(decodeURIComponent(escape(atob(payload.replace(/-/g, "+").replace(/_/g, "/")))));
  const realmRoles: unknown = json.realm_access?.roles;
  const roles = Array.isArray(realmRoles) ? realmRoles : [];
  return {
    id: json.user_id,
    email: json.email,
    name: json.name,
    cpf: json.cpf,
    phone: json.phone,
    role: APP_ROLES.find((role) => roles.includes(role)) ?? null,
    type: json.type,
    emailVerified: typeof json.email_verified === "boolean" ? json.email_verified : undefined,
    exp: typeof json.exp === "number" ? json.exp : undefined,
  };
}

export function isExpired(decoded: DecodedToken): boolean {
  return decoded.exp !== undefined && decoded.exp * 1000 <= Date.now();
}

// Only internal paths: "/x" is fine, "//host" and "/\host" are not.
export function safeNext(next: string | null | undefined): string | null {
  if (!next || !next.startsWith("/") || next.startsWith("//") || next.startsWith("/\\")) return null;
  return next;
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

// Cadastro continua no customer-management, que cria o usuário no Keycloak com a
// senha escolhida; o login em seguida é pelo Keycloak (sem login por senha aqui).
export async function registerCustomer(data: CustomerRegistrationRequest): Promise<Customer> {
  return apiFetch<Customer>(API_BASE.customers, "/api/v1/customers/register/customer", {
    method: "POST",
    body: data,
    auth: false,
  });
}
