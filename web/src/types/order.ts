// Mirrors order-service's CartResponseDto/CartItemResponseDto and
// OrderResponseDto/OrderItemResponseDto (api/rest/dto)
export type OrderStatus = "AWAITING_PAYMENT" | "PAID" | "READY_FOR_PICKUP" | "CANCELED";
export type CartStatus = "OPEN" | "CHECKED_OUT" | "EXPIRED";
export type StockAvailability = "AVAILABLE" | "LOW" | "UNAVAILABLE";

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
  // Até quando o estoque fica reservado esperando pagamento.
  expiresAt: string | null;
}

export interface CartItem {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
  // Disponibilidade atual (o carrinho não reserva estoque). null quando o
  // order-service não conseguiu consultar o catálogo nessa leitura.
  availability: StockAvailability | null;
}

export interface Cart {
  id: string;
  customerId: string;
  items: CartItem[];
  status: CartStatus;
  total: number; // só dos itens não indisponíveis
  expiresAt: string;
  // Produtos que saíram do carrinho nesta leitura por não estarem mais à venda.
  removedItems: string[];
}

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  AWAITING_PAYMENT: "Aguardando pagamento",
  PAID: "Em preparo",
  READY_FOR_PICKUP: "Pronto para retirada",
  CANCELED: "Cancelado",
};
