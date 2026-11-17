import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import type { Product } from "../../types/product";
import { StockBadge } from "./StockBadge";
import { ProductArt } from "./ProductArt";
import { CheckIcon, PlusIcon } from "../ui/Icon";
import { useCart } from "../../context/CartContext";
import { useAuth } from "../../context/AuthContext";
import { ApiError } from "../../services/http";
import { formatCurrency } from "../../utils/format";

export function ProductCard({ product }: { product: Product }) {
  const { addItem } = useCart();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [adding, setAdding] = useState(false);
  const [added, setAdded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // The "added" tick is a brief confirmation, then the button is a plus again.
  useEffect(() => {
    if (!added) return;
    const timer = window.setTimeout(() => setAdded(false), 1800);
    return () => window.clearTimeout(timer);
  }, [added]);

  async function handleAdd() {
    if (!isAuthenticated) {
      navigate("/entrar", { state: { from: { pathname: "/" } } });
      return;
    }
    setAdding(true);
    setError(null);
    try {
      await addItem(product.id, 1);
      setAdded(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível adicionar ao carrinho.");
    } finally {
      setAdding(false);
    }
  }

  return (
    <article className="product-card">
      <Link to={`/produtos/${product.id}`} className="product-art-link" tabIndex={-1} aria-hidden="true">
        <ProductArt name={product.name} seed={product.id} out={!product.inStock} />
      </Link>
      <div className="product-body">
        <h3 className="product-name">
          <Link to={`/produtos/${product.id}`}>{product.name}</Link>
        </h3>
        <div>
          <StockBadge stockRange={product.stockRange} />
        </div>
        <div className="product-foot">
          <span className="price-tag">{formatCurrency(product.price)}</span>
          <button
            type="button"
            className={added ? "add-btn is-done" : "add-btn"}
            disabled={adding || !product.inStock}
            onClick={handleAdd}
            aria-label={added ? `${product.name} adicionado ao carrinho` : `Adicionar ${product.name} ao carrinho`}
            title={product.inStock ? "Adicionar ao carrinho" : "Sem estoque"}
          >
            {added ? <CheckIcon size={20} /> : <PlusIcon size={20} />}
          </button>
        </div>
        {error && (
          <p className="product-error" role="alert">
            {error}
          </p>
        )}
      </div>
    </article>
  );
}
