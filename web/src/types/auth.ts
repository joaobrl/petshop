import type { Role, AccountType } from "./staff";

// Mirrors customer-management's LoginResponseDto (api/rest/dto).
export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  role: Role;
  type: AccountType;
  mustChangePassword: boolean;
}
