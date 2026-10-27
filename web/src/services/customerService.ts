import { apiFetch } from "./http";
import { API_BASE } from "./config";
import type { Customer, PetRequest } from "../types/customer";

export async function getCustomer(id: string): Promise<Customer> {
  return apiFetch<Customer>(API_BASE.customers, `/api/v1/customers/find/customer/${id}`);
}

// The backend takes an array (it supports adding several pets at once); we
// always send one and read the updated customer back.
export async function addPet(customerId: string, pet: PetRequest): Promise<Customer> {
  return apiFetch<Customer>(API_BASE.customers, `/api/v1/customers/register/${customerId}/pet`, {
    method: "PATCH",
    body: [pet],
  });
}

export async function updatePet(customerId: string, petId: string, pet: Partial<PetRequest>): Promise<Customer> {
  return apiFetch<Customer>(API_BASE.customers, `/api/v1/customers/${customerId}/pets/${petId}`, {
    method: "PATCH",
    body: pet,
  });
}

export async function deletePet(customerId: string, petId: string): Promise<Customer> {
  return apiFetch<Customer>(API_BASE.customers, `/api/v1/customers/${customerId}/pets/${petId}`, {
    method: "DELETE",
  });
}
