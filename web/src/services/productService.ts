import { apiFetch } from "./http";
import { API_BASE } from "./config";
import type { Product } from "../types/product";
import type { PageResponse } from "../types/common";

export async function listProducts(): Promise<Product[]> {
  const page = await apiFetch<PageResponse<Product>>(API_BASE.products, "/api/v1/products/list?size=50");
  return page.content;
}

export async function getProduct(id: number): Promise<Product> {
  return apiFetch<Product>(API_BASE.products, `/api/v1/products/find/${id}`);
}
