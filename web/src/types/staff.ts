// Mirrors petshop-commons Role enum, customer-management's StaffResponseDto
// (api/rest/dto) and staff-service's WeekendAllocationResponseDto.
export type Role = "ADMIN" | "RECEPTIONIST" | "GROOMER" | "VETERINARIAN" | "CUSTOMER" | "SERVICE";
export type AccountType = "CUSTOMER" | "STAFF" | "SERVICE";

export interface Staff {
  id: string;
  name: string;
  cpf: string;
  email: string;
  phone: string;
  enabled: boolean;
  role: Role;
}

export interface WeekendAllocation {
  allocationDate: string; // ISO date (LocalDate)
  staffId: string;
  staffName: string;
}

export const ROLE_LABEL: Record<Role, string> = {
  ADMIN: "Administrador(a)",
  RECEPTIONIST: "Recepção",
  GROOMER: "Tosador(a)",
  VETERINARIAN: "Veterinário(a)",
  CUSTOMER: "Cliente",
  SERVICE: "Serviço",
};
