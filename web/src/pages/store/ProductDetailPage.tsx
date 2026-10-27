import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import type { Product } from "../../types/product";
import { getProduct } from "../../services/productService";
import { ApiError } from "../../services/http";
import { Button } from "../../components/ui/Button";
import { useCart } from "../../context/CartContext";
import { useAuth } from "../../context/AuthContext";

export function ProductDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { addItem } = useCart();
  const { isAuthenticated } = useAuth();
  const [product, setProduct] = useState<Product | null | undefined>(undefined);
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    getProduct(Number(id))
      .then(setProduct)
      .catch(() => setProduct(null));
  }, [id]);

  if (product === undefined) return <p className="body">Carregando…</p>;
  if (product === null) return <p className="body">Produto não encontrado.</p>;

  async function handleAdd() {
    if (!isAuthenticated) {
      navigate("/entrar", { state: { from: { pathname: `/produtos/${id}` } } });
      return;
    }
    setAdding(true);
    setError(null);
    try {
      await addItem(product!.id, quantity);
      navigate("/carrinho");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível adicionar ao carrinho.");
    } finally {
      setAdding(false);
    }
  }

  return (
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "var(--space-8)" }}>
      <div
        style={{
          background: "var(--primary-tint)",
          borderRadius: "var(--radius-md)",
          height: 320,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          color: "var(--ink-muted)",
        }}
        className="body"
      >
        foto do produto
      </div>

      <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
        <h1 className="title">{product.name}</h1>
        <p className="body" style={{ color: "var(--ink-muted)" }}>
          {product.description}
        </p>
        <p className="display" style={{ fontSize: 28, lineHeight: "36px" }}>
          {product.price.toLocaleString("pt-BR", { style: "currency", currency: "BRL" })}
        </p>

        <div className="field" style={{ maxWidth: 120 }}>
          <label htmlFor="qty">Quantidade</label>
          <input
            id="qty"
            type="number"
            min={1}
            value={quantity}
            onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
          />
        </div>

        {error && (
          <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
            {error}
          </p>
        )}

        <div style={{ display: "flex", gap: "var(--space-3)" }}>
          <Button variant="primary" disabled={adding} onClick={handleAdd}>
            {adding ? "Adicionando…" : "Adicionar ao carrinho"}
          </Button>
          <Button variant="outline" onClick={() => navigate(-1)}>
            Voltar
          </Button>
        </div>
      </div>
    </div>
  );
}
