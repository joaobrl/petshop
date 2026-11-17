import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useCart } from "../../context/CartContext";
import { useAuth } from "../../context/AuthContext";
import * as orderService from "../../services/orderService";
import { ApiError } from "../../services/http";
import { Button } from "../../components/ui/Button";
import { EmptyState, Notice, PageHeader } from "../../components/ui/Feedback";
import { PinIcon } from "../../components/ui/Icon";
import { formatCurrency } from "../../utils/format";

const PAYMENT_OPTIONS = [
  { value: "PIX", label: "Pix", sub: "Na hora, pelo celular" },
  { value: "CARTAO", label: "Cartão", sub: "Crédito ou débito" },
  { value: "DINHEIRO", label: "Dinheiro", sub: "Paga na retirada" },
];

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
    return (
      <div className="container page">
        <EmptyState title="Nenhum item selecionado" note="o balcão está esperando">
          <p className="muted">Volte ao carrinho para escolher o que comprar.</p>
          <Link to="/carrinho" className="btn btn-primary">
            Ir para o carrinho
          </Link>
        </EmptyState>
      </div>
    );
  }

  const total = items.reduce((sum, item) => sum + item.subtotal, 0);

  async function handleConfirm() {
    if (!user) return;
    setPlacing(true);
    setErrors([]);
    try {
      const order = await orderService.checkout(
        user.id,
        items.map((item) => item.productId),
      );
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
    <div className="container page">
      <PageHeader eyebrow="último passo" title="Fechar o pedido" />

      <div className="split">
        <div className="stack" style={{ "--gap": "var(--space-8)" } as React.CSSProperties}>
          <section className="card pickup">
            <span className="pickup-pin">
              <PinIcon size={26} />
            </span>
            <div>
              <h2 className="heading">Retirada no balcão</h2>
              <p className="muted" style={{ marginTop: 4 }}>
                {user?.name} retira na loja — Rua das Patinhas, 123. Avisamos por e-mail quando estiver pronto.
              </p>
            </div>
          </section>

          <fieldset className="fieldset">
            <legend>Como você prefere pagar?</legend>
            <div className="choices" style={{ "--choice-min": "170px" } as React.CSSProperties}>
              {PAYMENT_OPTIONS.map((option) => (
                <label key={option.value} className="choice choice-col">
                  <input
                    type="radio"
                    name="payment"
                    value={option.value}
                    checked={paymentMethod === option.value}
                    onChange={() => setPaymentMethod(option.value)}
                  />
                  <span className="choice-title">{option.label}</span>
                  <span className="choice-sub">{option.sub}</span>
                </label>
              ))}
            </div>
            <div style={{ marginTop: "var(--space-4)" }}>
              <Notice tone="muted">
                Pagamento simulado — o checkout real não recebe forma de pagamento; o pedido só fica em "aguardando
                pagamento". O estoque fica reservado por 30 minutos; os itens não selecionados continuam no carrinho.
              </Notice>
            </div>
          </fieldset>

          {errors.length > 0 && (
            <Notice tone="danger">
              <ul>
                {errors.map((message) => (
                  <li key={message}>{message}</li>
                ))}
              </ul>
            </Notice>
          )}
        </div>

        <aside className="split-aside receipt-wrap" aria-label="Resumo do pedido">
          <div className="receipt">
            <div className="receipt-head">
              <strong>Resumo do pedido</strong>
              {user?.name}
            </div>
            {items.map((item) => (
              <div key={item.productId} className="receipt-line">
                <span>
                  {item.quantity}x {item.productName}
                </span>
                <span>{formatCurrency(item.subtotal)}</span>
              </div>
            ))}
            <div className="receipt-line receipt-total">
              <span>Total</span>
              <span>{formatCurrency(total)}</span>
            </div>
            <Button variant="primary" size="lg" block disabled={placing} onClick={handleConfirm}>
              {placing ? "Confirmando…" : "Confirmar pedido"}
            </Button>
          </div>
        </aside>
      </div>
    </div>
  );
}
