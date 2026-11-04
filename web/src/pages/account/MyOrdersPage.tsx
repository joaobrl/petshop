import * as orderService from "../../services/orderService";
import { useApi } from "../../hooks/useApi";
import { ErrorState } from "../../components/ErrorState";
import { OrderStatusBadge } from "../../components/ui/StatusBadges";

const currency = (value: number) => value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const dateTime = (iso: string) => new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
const time = (iso: string) => new Date(iso).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });

export function MyOrdersPage() {
  const { data: orders, error, retry } = useApi(() => orderService.listMyOrders(), []);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)", maxWidth: 640 }}>
      <h1 className="title">Meus pedidos</h1>
      <p className="caption" style={{ color: "var(--ink-muted)", margin: 0 }}>
        O backend ainda não tem um endpoint para listar todos os pedidos de um cliente — esta lista
        mostra os pedidos feitos neste navegador.
      </p>
      {error && <ErrorState message={error} onRetry={retry} />}
      {!orders && !error && <p className="body">Carregando…</p>}
      {orders?.length === 0 && <p className="body">Você ainda não fez nenhum pedido.</p>}
      {orders?.map((order) => (
        <div key={order.id} className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span className="body-strong">Pedido #{order.id.slice(0, 8)}</span>
            <OrderStatusBadge status={order.status} />
          </div>
          <span className="caption" style={{ color: "var(--ink-muted)" }}>
            {dateTime(order.createdAt)} · {order.items.length} {order.items.length === 1 ? "item" : "itens"}
          </span>
          <span className="body-strong">{currency(order.totalAmount)}</span>
          {order.status === "AWAITING_PAYMENT" && order.expiresAt && (
            <span className="caption" style={{ color: "var(--ink-muted)" }}>
              Reservado até {time(order.expiresAt)}
            </span>
          )}
        </div>
      ))}
    </div>
  );
}
