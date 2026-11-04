import { useParams, Link } from "react-router-dom";
import * as orderService from "../../services/orderService";
import { ApiError } from "../../services/http";
import { useApi } from "../../hooks/useApi";
import { ErrorState } from "../../components/ErrorState";
import { CheckCircleIcon } from "../../components/ui/Icon";
import { OrderStatusBadge } from "../../components/ui/StatusBadges";

const time = (iso: string) => new Date(iso).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" });

export function OrderConfirmationPage() {
  const { id } = useParams();
  // 404 means "not found"; any other failure is a load error with retry.
  const { data: order, error, retry } = useApi(
    () =>
      orderService.getOrder(id!).catch((e: unknown) => {
        if (e instanceof ApiError && e.status === 404) return null;
        throw e;
      }),
    [id],
    !!id,
  );

  if (error) return <ErrorState message={error} onRetry={retry} />;
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
      {order.status === "AWAITING_PAYMENT" && order.expiresAt && (
        <p className="caption" style={{ color: "var(--ink-muted)", margin: 0 }}>
          Reservado até {time(order.expiresAt)}
        </p>
      )}
      <Link to="/minha-conta/pedidos" className="btn btn-primary" style={{ textDecoration: "none", marginTop: "var(--space-4)" }}>
        Ver meus pedidos
      </Link>
      <Link to="/" className="btn btn-outline" style={{ textDecoration: "none" }}>
        Voltar ao catálogo
      </Link>
    </div>
  );
}
