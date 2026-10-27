import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import * as orderService from "../../services/orderService";
import type { Order } from "../../types/order";
import { CheckCircleIcon } from "../../components/ui/Icon";
import { OrderStatusBadge } from "../../components/ui/StatusBadges";

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

  if (order === undefined) return <p className="body">Carregando…</p>;
  if (order === null) return <p className="body">Pedido não encontrado.</p>;

  return (
    <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "var(--space-4)", textAlign: "center", maxWidth: 420, margin: "0 auto" }}>
      <CheckCircleIcon size={56} />
      <h1 className="title">Pedido confirmado!</h1>
      <p className="body" style={{ color: "var(--ink-muted)" }}>
        Avisamos por e-mail quando o pedido #{order.id.slice(0, 8)} estiver pronto para retirada.
      </p>
      <OrderStatusBadge status={order.status} />
      <Link to="/minha-conta/pedidos" className="btn btn-primary" style={{ textDecoration: "none", marginTop: "var(--space-4)" }}>
        Ver meus pedidos
      </Link>
      <Link to="/" className="btn btn-outline" style={{ textDecoration: "none" }}>
        Voltar ao catálogo
      </Link>
    </div>
  );
}
