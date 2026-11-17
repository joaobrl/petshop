import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import * as orderService from "../../services/orderService";
import type { Order } from "../../types/order";
import { EmptyState, Loading } from "../../components/ui/Feedback";
import { OrderStatusBadge } from "../../components/ui/StatusBadges";
import { formatDateTime } from "../../utils/date";
import { formatCurrency } from "../../utils/format";

export function OrderConfirmationPage() {
  const { id } = useParams();
  const [order, setOrder] = useState<Order | null | undefined>(undefined);

  useEffect(() => {
    if (!id) return;
    orderService
      .getOrder(id)
      .then(setOrder)
      .catch(() => setOrder(null));
  }, [id]);

  if (order === undefined) {
    return (
      <div className="container page">
        <Loading>Conferindo o pedido…</Loading>
      </div>
    );
  }
  if (order === null) {
    return (
      <div className="container page">
        <EmptyState title="Pedido não encontrado" note="esse a gente não achou no caderninho">
          <Link to="/" className="btn btn-primary">
            Voltar para a loja
          </Link>
        </EmptyState>
      </div>
    );
  }

  return (
    <div className="container page">
      <div className="confirm">
        <div style={{ textAlign: "center" }}>
          <span className="eyebrow">eba!</span>
          <h1 className="title">Pedido confirmado</h1>
        </div>

        <div className="ticket-wrap">
          <div className="ticket">
            <div className="ticket-top">
              <p className="label">Senha de retirada</p>
              <p className="ticket-number">#{order.id.slice(0, 8)}</p>
            </div>
            <div className="ticket-body stack" style={{ alignItems: "center" }}>
              <OrderStatusBadge status={order.status} />
              <p className="muted">
                Avisamos por e-mail quando o pedido estiver pronto para retirada na Rua das Patinhas, 123.
              </p>
              <p className="mono body-strong">
                {order.items.length} {order.items.length === 1 ? "item" : "itens"} · {formatCurrency(order.totalAmount)}
              </p>
              {order.status === "AWAITING_PAYMENT" && order.expiresAt && (
                <p className="hand" style={{ color: "var(--primary-ink)" }}>
                  reservado até {formatDateTime(order.expiresAt)}
                </p>
              )}
            </div>
          </div>
        </div>

        <div className="confirm-actions">
          <Link to="/minha-conta/pedidos" className="btn btn-primary btn-block">
            Ver meus pedidos
          </Link>
          <Link to="/" className="btn btn-outline btn-block">
            Voltar para a loja
          </Link>
        </div>
      </div>
    </div>
  );
}
