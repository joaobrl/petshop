// Mirrors product-registration's ProductResponseDto. Stock fields are only
// populated when the caller is a staff account (ADMIN/RECEPTIONIST/...) —
// a CUSTOMER token, or no token at all, gets them omitted entirely.
export interface Dimensions {
  weight: number;
  height: number;
  width: number;
  length: number;
}

export interface Product {
  id: number;
  name: string;
  description: string;
  price: number;
  stock?: number;
  reservedStock?: number;
  availableStock?: number;
  reservedPercentage?: number;
  dimensions: Dimensions;
}
