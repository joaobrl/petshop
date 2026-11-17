import type { ServiceType } from "../types/booking";

// Display copy for each bookable service (labels live in types/booking).
export const SERVICE_BLURB: Record<ServiceType, string> = {
  BANHO: "Água morninha, shampoo neutro e secagem sem pressa.",
  TOSAGEM: "Higiênica ou na tesoura, do jeitinho que você combinar.",
  TOSA_E_BANHO: "O combo completo: entra peludo, sai cheiroso.",
  CONSULTA_VETERINARIA: "Check-up, vacinas e aquela conversa com quem entende.",
};

export const SERVICE_TILE: Record<ServiceType, string> = {
  BANHO: "var(--bath-tint)",
  TOSAGEM: "var(--primary-tint)",
  TOSA_E_BANHO: "var(--snout-tint)",
  CONSULTA_VETERINARIA: "var(--grass-tint)",
};

export function isServiceType(value: string | null): value is ServiceType {
  return value !== null && value in SERVICE_BLURB;
}
