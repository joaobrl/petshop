import { apiFetch } from "./http";
import { API_BASE } from "./config";
import type { Staff, WeekendAllocation } from "../types/staff";
import type { PageResponse } from "../types/common";

// Staff roster lives in customer-management (role ADMIN/SERVICE); staff-service
// only exposes availability checks and the weekend allocation.
export async function listStaff(): Promise<Staff[]> {
  const page = await apiFetch<PageResponse<Staff>>(API_BASE.customers, "/api/v1/staff/all?size=50");
  return page.content;
}

export async function getWeekendAllocation(date: string): Promise<WeekendAllocation[]> {
  return apiFetch<WeekendAllocation[]>(API_BASE.staff, `/api/v1/weekend-allocation?date=${date}`);
}
