import { Link, useNavigate } from "react-router-dom";
import { useCart } from "../../context/CartContext";
import { Button } from "../../components/ui/Button";
import { PawBackground } from "../../components/ui/PawBackground";

const currency = (value: number) => value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });

export function CartPage() {
  const { cart, loading, addItem, removeItem } = useCart();
  const navigate = useNavigate();

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
          <Link to="/" className="btn btn-primary" style={{ marginTop: "var(--space-4)", textDecoration: "none" }}>
            Ver catálogo
          </Link>
        </div>
      </PawBackground>
    );
  }

  if (!cart) return <p className="body">Carregando…</p>;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-6)", maxWidth: 640 }}>
      <h1 className="title">Carrinho</h1>

      <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
        {cart.items.map((item) => (
          <div
            key={item.productId}
            className="card"
            style={{ display: "flex", alignItems: "center", gap: "var(--space-4)" }}
          >
            <div style={{ flex: 1 }}>
              <p className="body-strong" style={{ margin: 0 }}>
                {item.productName}
              </p>
              <p className="caption" style={{ margin: "4px 0 0", color: "var(--ink-muted)" }}>
                {currency(item.unitPrice)} cada · {item.quantity} {item.quantity === 1 ? "unidade" : "unidades"}
              </p>
            </div>
            <Button variant="outline" size="sm" onClick={() => addItem(item.productId, 1)}>
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
        ))}
      </div>

      <div
        className="card"
        style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}
      >
        <span className="heading">Total</span>
        <span className="title">{currency(cart.total)}</span>
      </div>

      <div style={{ display: "flex", gap: "var(--space-3)" }}>
        <Button variant="outline" onClick={() => navigate("/")}>
          Continuar comprando
        </Button>
        <Button variant="primary" onClick={() => navigate("/checkout")}>
          Ir para o checkout
        </Button>
      </div>
    </div>
  );
}
