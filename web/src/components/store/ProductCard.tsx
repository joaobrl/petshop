import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import type { Product } from "../../types/product";
import { Button } from "../ui/Button";
import { StockBadge } from "./StockBadge";
import { useCart } from "../../context/CartContext";
import { useAuth } from "../../context/AuthContext";
import { ApiError } from "../../services/http";

export function ProductCard({ product }: { product: Product }) {
  const { addItem } = useCart();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleAdd() {
    if (!isAuthenticated) {
      navigate("/entrar", { state: { from: { pathname: "/" } } });
      return;
    }
    setAdding(true);
    setError(null);
    try {
      await addItem(product.id, 1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível adicionar ao carrinho.");
    } finally {
      setAdding(false);
    }
  }

  return (
    <div className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
      <Link to={`/produtos/${product.id}`} style={{ textDecoration: "none" }}>
        <div
          style={{
            background: "var(--primary-tint)",
            borderRadius: "var(--radius-sm)",
            height: 140,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "var(--ink-muted)",
          }}
          className="caption"
        >
          foto do produto
        </div>
      </Link>
      <div>
        <Link to={`/produtos/${product.id}`} style={{ textDecoration: "none", color: "inherit" }}>
          <h3
            className="heading"
            style={{
              display: "-webkit-box",
              WebkitLineClamp: 2,
              WebkitBoxOrient: "vertical",
              overflow: "hidden",
            }}
          >
            {product.name}
          </h3>
        </Link>
      </div>
      <p className="body-strong">
        {product.price.toLocaleString("pt-BR", { style: "currency", currency: "BRL" })}
      </p>
      <div>
        <StockBadge stockRange={product.stockRange} />
      </div>
      <Button variant="primary" disabled={adding || !product.inStock} onClick={handleAdd}>
        {adding ? "Adicionando…" : "Adicionar ao carrinho"}
      </Button>
      {error && (
        <p className="caption" style={{ color: "var(--danger)", margin: 0 }}>
          {error}
        </p>
      )}
    </div>
  );
}
