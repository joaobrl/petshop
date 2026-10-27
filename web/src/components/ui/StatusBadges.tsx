import { Badge } from "./Badge";
import type { OrderStatus } from "../../types/order";
import { ORDER_STATUS_LABEL } from "../../types/order";
import type { StatusBooking } from "../../types/booking";

const ORDER_TONE: Record<OrderStatus, "warning" | "info" | "success" | "danger"> = {
  AWAITING_PAYMENT: "warning",
  PAID: "info",
  READY_FOR_PICKUP: "success",
  CANCELED: "danger",
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return <Badge tone={ORDER_TONE[status]}>{ORDER_STATUS_LABEL[status]}</Badge>;
}

const BOOKING_TONE: Record<StatusBooking, "warning" | "info" | "success" | "danger"> = {
  SCHEDULED: "info",
  COMPLETED: "success",
  CANCELED: "danger",
};

const BOOKING_LABEL: Record<StatusBooking, string> = {
  SCHEDULED: "Agendado",
  COMPLETED: "Concluído",
  CANCELED: "Cancelado",
};

export function BookingStatusBadge({ status }: { status: StatusBooking }) {
  return <Badge tone={BOOKING_TONE[status]}>{BOOKING_LABEL[status]}</Badge>;
}
