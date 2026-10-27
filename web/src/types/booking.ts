// Mirrors booking-service's BookingRequestDto (core/port/in/dto) and
// BookingResponseDto/ServiceDetailsResponseDto/AvailableSlotResponseDto
// (core/port/out/dto). Dates travel as "dd/MM/yyyy HH:mm" strings, not ISO.
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
  bookingDateTime: string; // "dd/MM/yyyy HH:mm"
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
  bookingDateTime: string; // "dd/MM/yyyy HH:mm"
  status: StatusBooking;
  observations: string;
}

export interface AvailableSlot {
  dateTime: string; // "dd/MM/yyyy HH:mm"
  availableStaff: number;
}

export const SERVICE_TYPE_LABEL: Record<ServiceType, string> = {
  TOSAGEM: "Tosa",
  BANHO: "Banho",
  TOSA_E_BANHO: "Tosa e banho",
  CONSULTA_VETERINARIA: "Consulta veterinária",
};

// dd/MM/yyyy HH:mm <-> JS Date, matching the backend's @JsonFormat pattern.
export function formatBookingDateTime(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(date.getDate())}/${pad(date.getMonth() + 1)}/${date.getFullYear()} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function parseBookingDateTime(value: string): Date {
  const [datePart, timePart] = value.split(" ");
  const [day, month, year] = datePart.split("/").map(Number);
  const [hour, minute] = (timePart ?? "00:00").split(":").map(Number);
  return new Date(year, month - 1, day, hour, minute);
}
