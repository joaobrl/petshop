import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import * as orderService from "../../services/orderService";
import type { Order } from "../../types/order";
import { OrderStatusBadge } from "../../components/ui/StatusBadges";
import { EmptyState, Loading, Notice, PageHeader } from "../../components/ui/Feedback";
import { formatDateTime } from "../../utils/date";
import { formatCurrency } from "../../utils/format";

export function MyOrdersPage() {
  const [orders, setOrders] = useState<Order[] | null>(null);

  useEffect(() => {
    orderService.listMyOrders().then(setOrders);
  }, []);

  return (
    <div className="container page page-narrow">
      <PageHeader eyebrow="o que você já levou" title="Meus pedidos" />

      <div className="stack">
        <Notice tone="muted">
          O backend ainda não tem um endpoint para listar todos os pedidos de um cliente — esta lista mostra os
          pedidos feitos neste navegador.
        </Notice>

        {orders === null && <Loading>Folheando o caderninho…</Loading>}

        {orders?.length === 0 && (
          <EmptyState title="Você ainda não fez nenhum pedido" note="a prateleira está logo ali">
            <Link to="/" className="btn btn-primary">
              Ver a prateleira
            </Link>
          </EmptyState>
        )}

        {orders && orders.length > 0 && (
          <ul className="list">
            {orders.map((order) => (
              <li key={order.id} className="list-row">
                <div className="list-row-main">
                  <p className="list-row-title mono">
                    <Link to={`/pedido/${order.id}`} className="link">
                      Pedido #{order.id.slice(0, 8)}
                    </Link>
                  </p>
                  <p className="list-row-sub">
                    {formatDateTime(order.createdAt)} · {order.items.length}{" "}
                    {order.items.length === 1 ? "item" : "itens"}
                  </p>
                  <ul className="order-items">
                    {order.items.map((item) => (
                      <li key={item.productId}>
                        {item.quantity}x {item.productName}
                      </li>
                    ))}
                  </ul>
                  {order.status === "AWAITING_PAYMENT" && order.expiresAt && (
                    <p className="list-row-sub">Reservado até {formatDateTime(order.expiresAt)}</p>
                  )}
                </div>
                <div className="list-row-actions">
                  <OrderStatusBadge status={order.status} />
                  <span className="price-tag">{formatCurrency(order.totalAmount)}</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
