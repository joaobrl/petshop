// Mirrors booking-service's BookingRequestDto (core/port/in/dto) and
// BookingResponseDto/ServiceDetailsResponseDto/AvailableSlotResponseDto
// (core/port/out/dto). Dates travel as ISO-8601 strings without offset
// ("2026-08-15T09:00:00"), always Brasilia wall-clock time.
export type ServiceType = "TOSAGEM" | "BANHO" | "TOSA_E_BANHO" | "CONSULTA_VETERINARIA";
export type StatusBooking = "SCHEDULED" | "COMPLETED" | "CANCELED";
export type PaymentStatus = "PENDING" | "COMPLETED";
export type PaymentMethod = "PIX" | "DINHEIRO" | "CARTAO";
export type RangeType = "DAY" | "WEEK" | "MONTH";

export interface ServiceDetails {
  serviceType: ServiceType;
  price: number;
  durationInMinutes: number;
  paymentStatus: PaymentStatus;
  paymentMethod: PaymentMethod;
}

export interface BookingRequest {
  petId: string;
  ownerName: string;
  ownerCpf: string;
  ownerContact: string;
  ownerEmail: string;
  serviceType: ServiceType;
  bookingDateTime: string; // ISO-8601, "yyyy-MM-ddTHH:mm:ss"
  observations?: string;
}

export interface Booking {
  id: string;
  petId: string;
  ownerName: string;
  ownerCpf: string;
  ownerContact: string;
  ownerEmail: string;
  serviceType: ServiceDetails; // yes, the response nests ServiceDetails under the "serviceType" key
  bookingDateTime: string; // ISO-8601, "yyyy-MM-ddTHH:mm:ss"
  status: StatusBooking;
  observations: string;
}

export interface AvailableSlot {
  dateTime: string; // ISO-8601, "yyyy-MM-ddTHH:mm:ss"
  availableStaff: number;
}

export const SERVICE_TYPE_LABEL: Record<ServiceType, string> = {
  TOSAGEM: "Tosa",
  BANHO: "Banho",
  TOSA_E_BANHO: "Tosa e banho",
  CONSULTA_VETERINARIA: "Consulta veterinária",
};

// JS Date (wall-clock parts) -> ISO-8601 without offset, as booking-service expects.
// Reading/displaying goes through utils/date (formatDateTime), which understands ISO.
export function formatBookingDateTime(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:00`;
}
