import { apiFetch } from "./http";
import { API_BASE } from "./config";
import type { Cart, Order } from "../types/order";

export async function getCart(customerId: string): Promise<Cart> {
  return apiFetch<Cart>(API_BASE.orders, `/api/v1/cart?customerId=${customerId}`, { auth: false });
}

export async function addCartItem(customerId: string, productId: number, quantity: number): Promise<Cart> {
  return apiFetch<Cart>(API_BASE.orders, "/api/v1/cart/items", {
    method: "POST",
    body: { customerId, productId, quantity },
    auth: false,
  });
}

export async function removeCartItem(customerId: string, productId: number): Promise<Cart> {
  return apiFetch<Cart>(API_BASE.orders, `/api/v1/cart/items/${productId}?customerId=${customerId}`, {
    method: "DELETE",
    auth: false,
  });
}

// Only the selected cart items become an order (their stock is reserved
// here, not when added to the cart); the rest stay in the cart. Sending the
// same idempotencyKey again (retry, double click) returns the order already
// created instead of a second one.
export async function checkout(customerId: string, productIds: number[], idempotencyKey: string): Promise<Order> {
  return apiFetch<Order>(API_BASE.orders, `/api/v1/orders/checkout?customerId=${customerId}`, {
    method: "POST",
    body: { productIds },
    headers: { "Idempotency-Key": idempotencyKey },
  });
}

export async function getOrder(id: string): Promise<Order> {
  return apiFetch<Order>(API_BASE.orders, `/api/v1/orders/${id}`);
}

// order-service has no "list my orders" or "list all orders" endpoint (only
// GET /orders/{id}) and no staff status-update endpoint — see web/README.md.
// We track order ids this browser created so "Meus pedidos" has something to
// show; a real deployment would need the backend to grow a list endpoint.
const TRACKED_ORDERS_KEY = "petshop.orderIds";

export function trackOrderId(id: string) {
  const ids = getTrackedOrderIds();
  if (!ids.includes(id)) localStorage.setItem(TRACKED_ORDERS_KEY, JSON.stringify([id, ...ids]));
}

export function getTrackedOrderIds(): string[] {
  try {
    return JSON.parse(localStorage.getItem(TRACKED_ORDERS_KEY) ?? "[]");
  } catch {
    return [];
  }
}

export async function listMyOrders(): Promise<Order[]> {
  const ids = getTrackedOrderIds();
  const results = await Promise.allSettled(ids.map(getOrder));
  return results.filter((r): r is PromiseFulfilledResult<Order> => r.status === "fulfilled").map((r) => r.value);
}
