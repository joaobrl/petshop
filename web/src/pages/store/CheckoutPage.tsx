import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useCart } from "../../context/CartContext";
import { useAuth } from "../../context/AuthContext";
import * as orderService from "../../services/orderService";
import { ApiError } from "../../services/http";
import { Button } from "../../components/ui/Button";
import { SelectField } from "../../components/ui/Field";

const currency = (value: number) => value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });

export function CheckoutPage() {
  const { cart, refresh } = useCart();
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [paymentMethod, setPaymentMethod] = useState("PIX");
  const [placing, setPlacing] = useState(false);
  const [errors, setErrors] = useState<string[]>([]);

  // Selection made on the cart page; opening /checkout directly falls back to
  // every item that can be bought right now.
  const requestedIds = (location.state as { productIds?: number[] } | null)?.productIds;
  const items = (cart?.items ?? []).filter(
    (item) =>
      item.availability !== "UNAVAILABLE" && (requestedIds === undefined || requestedIds.includes(item.productId)),
  );

  if (!cart || items.length === 0) {
    return <p className="body">Nenhum item selecionado — volte ao carrinho para escolher o que comprar.</p>;
  }

  const total = items.reduce((sum, item) => sum + item.subtotal, 0);

  async function handleConfirm() {
    if (!user) return;
    setPlacing(true);
    setErrors([]);
    try {
      const order = await orderService.checkout(user.id, items.map((item) => item.productId));
      orderService.trackOrderId(order.id);
      await refresh();
      navigate(`/pedido/${order.id}`);
    } catch (err) {
      // 422 lists every item that couldn't be reserved, joined by " | ".
      setErrors(err instanceof ApiError ? err.message.split(" | ") : ["Não foi possível concluir o checkout."]);
    } finally {
      setPlacing(false);
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-6)", maxWidth: 520 }}>
      <h1 className="title">Checkout</h1>

      <div className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
        <span className="label" style={{ color: "var(--ink-muted)" }}>
          Retirada
        </span>
        <p className="body" style={{ margin: 0 }}>
          {user?.name} — retirada na loja, Rua das Patinhas, 123
        </p>
      </div>

      <div className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
        <span className="label" style={{ color: "var(--ink-muted)" }}>
          Resumo do pedido
        </span>
        {items.map((item) => (
          <div key={item.productId} style={{ display: "flex", justifyContent: "space-between" }}>
            <span className="body">
              {item.quantity}× {item.productName}
            </span>
            <span className="body">{currency(item.subtotal)}</span>
          </div>
        ))}
        <div style={{ borderTop: "1px solid var(--border)", paddingTop: "var(--space-2)", display: "flex", justifyContent: "space-between" }}>
          <span className="heading">Total</span>
          <span className="heading">{currency(total)}</span>
        </div>
      </div>

      <SelectField
        label="Forma de pagamento (simulado)"
        value={paymentMethod}
        onChange={(e) => setPaymentMethod(e.target.value)}
      >
        <option value="PIX">Pix</option>
        <option value="CARTAO">Cartão</option>
        <option value="DINHEIRO">Dinheiro na retirada</option>
      </SelectField>

      {errors.length > 0 && (
        <ul className="body" style={{ color: "var(--danger)", margin: 0, paddingLeft: "var(--space-4)" }}>
          {errors.map((message) => (
            <li key={message}>{message}</li>
          ))}
        </ul>
      )}

      <Button variant="primary" disabled={placing} onClick={handleConfirm}>
        {placing ? "Confirmando…" : "Confirmar pedido"}
      </Button>
      <p className="caption" style={{ color: "var(--ink-muted)" }}>
        Pagamento simulado — o checkout real não recebe forma de pagamento; o pedido só fica em
        "aguardando pagamento". O estoque fica reservado por 30 minutos; os itens não selecionados
        continuam no carrinho.
      </p>
    </div>
  );
}
