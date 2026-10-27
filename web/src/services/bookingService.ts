import { apiFetch } from "./http";
import { API_BASE } from "./config";
import type { AvailableSlot, Booking, BookingRequest, RangeType, ServiceType, StatusBooking } from "../types/booking";
import type { PageResponse } from "../types/common";

export async function getAvailableSlots(
  serviceType: ServiceType,
  date: string, // "dd/MM/yyyy"
  range: RangeType = "DAY",
): Promise<AvailableSlot[]> {
  const params = new URLSearchParams({ serviceType, date, range });
  return apiFetch<AvailableSlot[]>(API_BASE.bookings, `/api/v1/bookings/available-slots?${params}`, { auth: false });
}

export async function createBooking(request: BookingRequest): Promise<Booking> {
  return apiFetch<Booking>(API_BASE.bookings, "/api/v1/bookings/create", {
    method: "POST",
    body: request,
  });
}

export async function listBookings(filters: { ownerCpf?: string; status?: StatusBooking } = {}): Promise<Booking[]> {
  const params = new URLSearchParams({ size: "50", ...filters });
  const page = await apiFetch<PageResponse<Booking>>(API_BASE.bookings, `/api/v1/bookings/list?${params}`);
  return page.content;
}

export async function cancelBooking(id: string): Promise<Booking> {
  return apiFetch<Booking>(API_BASE.bookings, `/api/v1/bookings/cancel/${id}`, { method: "DELETE" });
}

export async function confirmBookingPayment(id: string, paymentMethod: "PIX" | "DINHEIRO" | "CARTAO"): Promise<Booking> {
  return apiFetch<Booking>(API_BASE.bookings, `/api/v1/bookings/confirm-payment/${id}`, {
    method: "PATCH",
    body: { paymentMethod },
  });
}

export async function updateBookingStatus(id: string, status: StatusBooking): Promise<Booking> {
  return apiFetch<Booking>(API_BASE.bookings, `/api/v1/bookings/update/${id}?status=${status}`, {
    method: "PATCH",
    body: {},
  });
}
