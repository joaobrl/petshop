// Mirrors product-registration's ProductResponseDto. Exact stock fields are only
// populated when the caller is a staff account (ADMIN/RECEPTIONIST/...) —
// a CUSTOMER token, or no token at all, gets them omitted entirely. Everyone
// gets inStock + stockRange (a band, never the exact quantity).
export interface Dimensions {
  weight: number;
  height: number;
  width: number;
  length: number;
}

export type StockRange = "50+" | "20+" | "LOW";

export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  stock?: number;
  reservedStock?: number;
  availableStock?: number;
  reservedPercentage?: number;
  inStock: boolean;
  // "50+", "20+", "LOW" (1–19) or null when out of stock.
  stockRange: StockRange | null;
  enabled: boolean;
  dimensions: Dimensions;
}
