import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useCart } from "../../context/CartContext";
import { Button } from "../../components/ui/Button";
import { EmptyState, Loading, Notice, PageHeader } from "../../components/ui/Feedback";
import { PlusIcon, TrashIcon } from "../../components/ui/Icon";
import { ProductArt } from "../../components/store/ProductArt";
import { formatCurrency } from "../../utils/format";

export function CartPage() {
  const { cart, loading, addItem, removeItem } = useCart();
  const navigate = useNavigate();
  // The cart doesn't reserve stock: the customer picks which items to buy
  // now. Available items start checked, so we only track the ones unchecked.
  const [unchecked, setUnchecked] = useState<Set<number>>(new Set());

  if (!loading && (!cart || cart.items.length === 0)) {
    return (
      <div className="container page">
        <EmptyState title="Seu carrinho está vazio" note="nem um biscoitinho ainda…">
          <p className="muted">Que tal dar uma olhada nos produtos para o seu pet?</p>
          {cart && cart.removedItems.length > 0 && (
            <Notice tone="warning">
              {cart.removedItems.join(", ")} saiu do carrinho porque não está mais à venda.
            </Notice>
          )}
          <Link to="/" className="btn btn-primary">
            Ver a prateleira
          </Link>
        </EmptyState>
      </div>
    );
  }

  if (!cart) {
    return (
      <div className="container page">
        <Loading>Abrindo o carrinho…</Loading>
      </div>
    );
  }

  const selectedItems = cart.items.filter(
    (item) => item.availability !== "UNAVAILABLE" && !unchecked.has(item.productId),
  );
  const selectedTotal = selectedItems.reduce((sum, item) => sum + item.subtotal, 0);
  const partial = selectedItems.length !== cart.items.length;

  function toggle(productId: number) {
    setUnchecked((current) => {
      const next = new Set(current);
      if (next.has(productId)) next.delete(productId);
      else next.add(productId);
      return next;
    });
  }

  return (
    <div className="container page">
      <PageHeader eyebrow="quase lá" title="Seu carrinho" />

      <div className="split">
        <div className="stack">
          {cart.removedItems.length > 0 && (
            <Notice tone="warning">
              {cart.removedItems.join(", ")} saiu do carrinho porque não está mais à venda.
            </Notice>
          )}

          <ul className="list">
            {cart.items.map((item) => {
              const unavailable = item.availability === "UNAVAILABLE";
              const checked = !unavailable && !unchecked.has(item.productId);
              return (
                <li key={item.productId} className={unavailable ? "cart-item is-unavailable" : "cart-item"}>
                  <input
                    type="checkbox"
                    className="check-box"
                    checked={checked}
                    disabled={unavailable}
                    onChange={() => toggle(item.productId)}
                    aria-label={`Comprar ${item.productName}`}
                  />
                  <Link to={`/produtos/${item.productId}`} className="cart-art" tabIndex={-1} aria-hidden="true">
                    <ProductArt name={item.productName} seed={item.productId} />
                  </Link>
                  <div className="cart-info">
                    <p className="list-row-title">{item.productName}</p>
                    <p className="list-row-sub">
                      {formatCurrency(item.unitPrice)} cada · {item.quantity}{" "}
                      {item.quantity === 1 ? "unidade" : "unidades"}
                    </p>
                    {item.availability === "LOW" && (
                      <p className="cart-flag" style={{ color: "var(--warning)" }}>
                        Poucas unidades
                      </p>
                    )}
                    {unavailable && (
                      <p className="cart-flag" style={{ color: "var(--danger)" }}>
                        Item indisponível
                      </p>
                    )}
                  </div>
                  <div className="cart-foot">
                    <Button
                      variant="outline"
                      size="sm"
                      disabled={unavailable}
                      onClick={() => addItem(item.productId, 1)}
                      aria-label={`Adicionar mais uma unidade de ${item.productName}`}
                    >
                      <PlusIcon size={16} />1
                    </Button>
                    <span className="body-strong mono">{formatCurrency(item.subtotal)}</span>
                  </div>
                  <button
                    type="button"
                    className="icon-btn icon-btn-danger"
                    onClick={() => removeItem(item.productId)}
                    aria-label={`Remover ${item.productName}`}
                    title="Remover"
                  >
                    <TrashIcon size={20} />
                  </button>
                </li>
              );
            })}
          </ul>

          <div>
            <Button variant="ghost" onClick={() => navigate("/")}>
              Continuar comprando
            </Button>
          </div>
        </div>

        <aside className="split-aside receipt-wrap" aria-label="Resumo">
          <div className="receipt">
            <div className="receipt-head">
              <strong>WebCommerce PetShop</strong>
              Rua das Patinhas, 123
            </div>
            {selectedItems.length === 0 ? (
              <p className="receipt-note">Marque ao menos um item para comprar agora.</p>
            ) : (
              selectedItems.map((item) => (
                <div key={item.productId} className="receipt-line">
                  <span>
                    {item.quantity}x {item.productName}
                  </span>
                  <span>{formatCurrency(item.subtotal)}</span>
                </div>
              ))
            )}
            <div className="receipt-line receipt-total">
              <span>Total</span>
              <span>{formatCurrency(selectedTotal)}</span>
            </div>
            {partial && <p className="receipt-note">Somando só os itens marcados. O resto fica guardado no carrinho.</p>}
            <Button
              variant="primary"
              size="lg"
              block
              disabled={selectedItems.length === 0}
              onClick={() =>
                navigate("/checkout", { state: { productIds: selectedItems.map((item) => item.productId) } })
              }
            >
              Finalizar compra
            </Button>
          </div>
        </aside>
      </div>
    </div>
  );
}
