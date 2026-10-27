import { useState } from "react";
import * as orderService from "../../services/orderService";
import { ApiError } from "../../services/http";
import { OrderStatusBadge } from "../../components/ui/StatusBadges";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/Field";
import type { Order } from "../../types/order";

const currency = (value: number) => value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const dateTime = (iso: string) => new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });

export function InternalOrdersPage() {
  const [orderId, setOrderId] = useState("");
  const [order, setOrder] = useState<Order | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function handleLookup(e: React.FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError(null);
    setOrder(null);
    try {
      setOrder(await orderService.getOrder(orderId.trim()));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Pedido não encontrado.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)", maxWidth: 480 }}>
      <h1 className="title">Pedidos</h1>
      <p className="caption" style={{ color: "var(--ink-muted)", margin: 0 }}>
        O order-service só expõe <code>GET /orders/{"{id}"}</code> — não há endpoint para listar todos
        os pedidos nem para a equipe atualizar o status. Busque um pedido pelo id que o cliente informar.
      </p>
      <form onSubmit={handleLookup} style={{ display: "flex", gap: "var(--space-3)", alignItems: "flex-end" }}>
        <div style={{ flex: 1 }}>
          <TextField label="Id do pedido" value={orderId} onChange={(e) => setOrderId(e.target.value)} required />
        </div>
        <Button variant="primary" type="submit" disabled={loading}>
          {loading ? "Buscando…" : "Buscar"}
        </Button>
      </form>

      {error && (
        <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
          {error}
        </p>
      )}

      {order && (
        <div className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span className="body-strong">Pedido #{order.id.slice(0, 8)}</span>
            <OrderStatusBadge status={order.status} />
          </div>
          <span className="caption" style={{ color: "var(--ink-muted)" }}>
            {dateTime(order.createdAt)}
          </span>
          {order.items.map((item) => (
            <div key={item.productId} style={{ display: "flex", justifyContent: "space-between" }}>
              <span className="body">
                {item.quantity}× {item.productName}
              </span>
            </div>
          ))}
          <div style={{ borderTop: "1px solid var(--border)", paddingTop: "var(--space-2)", display: "flex", justifyContent: "space-between" }}>
            <span className="heading">Total</span>
            <span className="heading">{currency(order.totalAmount)}</span>
          </div>
        </div>
      )}
    </div>
  );
}
