// Mirrors order-service's CartResponseDto/CartItemResponseDto and
// OrderResponseDto/OrderItemResponseDto (api/rest/dto)
export type OrderStatus = "AWAITING_PAYMENT" | "PAID" | "READY_FOR_PICKUP" | "CANCELED";
export type CartStatus = "OPEN" | "CHECKED_OUT" | "EXPIRED";

export interface OrderItem {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
}

export interface Order {
  id: string;
  customerId: string;
  items: OrderItem[];
  totalAmount: number;
  status: OrderStatus;
  createdAt: string;
  paidAt: string | null;
  pickupReadyAt: string | null;
}

export interface CartItem {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
  reserved: boolean;
}

export interface Cart {
  id: string;
  customerId: string;
  items: CartItem[];
  status: CartStatus;
  total: number;
  expiresAt: string;
}

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  AWAITING_PAYMENT: "Aguardando pagamento",
  PAID: "Em preparo",
  READY_FOR_PICKUP: "Pronto para retirada",
  CANCELED: "Cancelado",
};
