import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useCart } from "../../context/CartContext";
import { Button } from "../../components/ui/Button";
import { PawBackground } from "../../components/ui/PawBackground";

const currency = (value: number) => value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });

export function CartPage() {
  const { cart, loading, addItem, removeItem } = useCart();
  const navigate = useNavigate();
  // The cart doesn't reserve stock: the customer picks which items to buy
  // now. Available items start checked, so we only track the ones unchecked.
  const [unchecked, setUnchecked] = useState<Set<number>>(new Set());

  if (!loading && (!cart || cart.items.length === 0)) {
    return (
      <PawBackground tint="var(--secondary-tint)">
        <div style={{ padding: "var(--space-8) var(--space-6)", maxWidth: 320 }}>
          <p className="heading" style={{ margin: 0 }}>
            Seu carrinho está vazio
          </p>
          <p className="body" style={{ margin: "var(--space-2) 0 0", color: "var(--ink-muted)" }}>
            Que tal dar uma olhada nos produtos para o seu pet?
          </p>
          {cart && cart.removedItems.length > 0 && (
            <p className="caption" style={{ margin: "var(--space-2) 0 0", color: "var(--ink-muted)" }}>
              {cart.removedItems.join(", ")} saiu do carrinho porque não está mais à venda.
            </p>
          )}
          <Link to="/" className="btn btn-primary" style={{ marginTop: "var(--space-4)", textDecoration: "none" }}>
            Ver catálogo
          </Link>
        </div>
      </PawBackground>
    );
  }

  if (!cart) return <p className="body">Carregando…</p>;

  const selectedItems = cart.items.filter(
    (item) => item.availability !== "UNAVAILABLE" && !unchecked.has(item.productId),
  );
  const selectedTotal = selectedItems.reduce((sum, item) => sum + item.subtotal, 0);

  function toggle(productId: number) {
    setUnchecked((current) => {
      const next = new Set(current);
      if (next.has(productId)) next.delete(productId);
      else next.add(productId);
      return next;
    });
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-6)", maxWidth: 640 }}>
      <h1 className="title">Carrinho</h1>

      {cart.removedItems.length > 0 && (
        <p className="caption" style={{ margin: 0, color: "var(--ink-muted)" }}>
          {cart.removedItems.join(", ")} saiu do carrinho porque não está mais à venda.
        </p>
      )}

      <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
        {cart.items.map((item) => {
          const unavailable = item.availability === "UNAVAILABLE";
          const checked = !unavailable && !unchecked.has(item.productId);
          return (
            <div key={item.productId} className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "var(--space-4)",
                  opacity: unavailable ? 0.5 : 1,
                  filter: unavailable ? "grayscale(1)" : undefined,
                }}
              >
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={unavailable}
                  onChange={() => toggle(item.productId)}
                  aria-label={`Comprar ${item.productName}`}
                />
                <div style={{ flex: 1 }}>
                  <p className="body-strong" style={{ margin: 0 }}>
                    {item.productName}
                  </p>
                  <p className="caption" style={{ margin: "4px 0 0", color: "var(--ink-muted)" }}>
                    {currency(item.unitPrice)} cada · {item.quantity} {item.quantity === 1 ? "unidade" : "unidades"}
                  </p>
                </div>
                <Button variant="outline" size="sm" disabled={unavailable} onClick={() => addItem(item.productId, 1)}>
                  +1
                </Button>
                <p className="body-strong" style={{ width: 90, textAlign: "right", margin: 0 }}>
                  {currency(item.subtotal)}
                </p>
                <button
                  className="btn btn-outline btn-sm"
                  onClick={() => removeItem(item.productId)}
                  aria-label="Remover item"
                >
                  Remover
                </button>
              </div>
              {item.availability === "LOW" && (
                <p className="caption" style={{ margin: 0, color: "var(--warning)" }}>
                  Poucas unidades
                </p>
              )}
              {unavailable && (
                <p className="caption" style={{ margin: 0, color: "var(--danger)" }}>
                  Item indisponível
                </p>
              )}
            </div>
          );
        })}
      </div>

      <div
        className="card"
        style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}
      >
        <span className="heading">
          Total {selectedItems.length !== cart.items.length && <span className="caption">(itens selecionados)</span>}
        </span>
        <span className="title">{currency(selectedTotal)}</span>
      </div>

      <div style={{ display: "flex", gap: "var(--space-3)" }}>
        <Button variant="outline" onClick={() => navigate("/")}>
          Continuar comprando
        </Button>
        <Button
          variant="primary"
          disabled={selectedItems.length === 0}
          onClick={() => navigate("/checkout", { state: { productIds: selectedItems.map((item) => item.productId) } })}
        >
          Finalizar compra
        </Button>
      </div>
    </div>
  );
}
