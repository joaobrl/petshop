import { useState } from "react";
import * as orderService from "../../services/orderService";
import { ApiError } from "../../services/http";
import { OrderStatusBadge } from "../../components/ui/StatusBadges";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/Field";
import { Notice, PageHeader } from "../../components/ui/Feedback";
import type { Order } from "../../types/order";
import { formatDateTime } from "../../utils/date";
import { formatCurrency } from "../../utils/format";

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
    <div className="stack" style={{ maxWidth: 560 }}>
      <PageHeader eyebrow="no balcão" title="Pedidos" />
      <Notice tone="muted">
        O order-service só expõe <code>GET /orders/{"{id}"}</code> — não há endpoint para listar todos os pedidos nem
        para a equipe atualizar o status. Busque um pedido pelo id que o cliente informar.
      </Notice>
      <form onSubmit={handleLookup} className="row row-wrap" style={{ alignItems: "flex-end" }}>
        <div style={{ flex: "1 1 220px" }}>
          <TextField label="Id do pedido" value={orderId} onChange={(e) => setOrderId(e.target.value)} required />
        </div>
        <Button variant="primary" type="submit" disabled={loading}>
          {loading ? "Buscando…" : "Buscar"}
        </Button>
      </form>

      {error && <Notice tone="danger">{error}</Notice>}

      {order && (
        <div className="receipt-wrap">
          <div className="receipt">
            <div className="receipt-head">
              <strong>Pedido #{order.id.slice(0, 8)}</strong>
              {formatDateTime(order.createdAt)}
              <div style={{ marginTop: 8 }}>
                <OrderStatusBadge status={order.status} />
              </div>
            </div>
            {order.items.map((item) => (
              <div key={item.productId} className="receipt-line">
                <span>
                  {item.quantity}x {item.productName}
                </span>
                <span>{formatCurrency(item.unitPrice * item.quantity)}</span>
              </div>
            ))}
            <div className="receipt-line receipt-total">
              <span>Total</span>
              <span>{formatCurrency(order.totalAmount)}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
