import { useEffect, useState } from "react";
import { Link, useParams, useNavigate } from "react-router-dom";
import type { Product } from "../../types/product";
import { getProduct } from "../../services/productService";
import { ApiError } from "../../services/http";
import { Button } from "../../components/ui/Button";
import { EmptyState, Loading, Notice } from "../../components/ui/Feedback";
import { ArrowRightIcon, CartIcon, MinusIcon, PinIcon, PlusIcon } from "../../components/ui/Icon";
import { useCart } from "../../context/CartContext";
import { useAuth } from "../../context/AuthContext";
import { StockBadge } from "../../components/store/StockBadge";
import { ProductArt } from "../../components/store/ProductArt";
import { formatCurrency } from "../../utils/format";

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

  if (product === undefined) {
    return (
      <div className="container page">
        <Loading>Buscando na prateleira…</Loading>
      </div>
    );
  }
  if (product === null) {
    return (
      <div className="container page">
        <EmptyState title="Produto não encontrado" note="procuramos até atrás do saco de ração">
          <Link to="/" className="btn btn-primary">
            Voltar para a loja
          </Link>
        </EmptyState>
      </div>
    );
  }

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

  const { dimensions } = product;

  return (
    <div className="container page">
      <button type="button" className="back-link" onClick={() => navigate(-1)}>
        <ArrowRightIcon size={18} />
        Voltar
      </button>

      <div className="product-detail">
        <ProductArt name={product.name} seed={product.id} out={!product.inStock} />

        <div className="product-info">
          <div>
            <StockBadge stockRange={product.stockRange} />
            <h1 className="title" style={{ marginTop: "var(--space-3)" }}>
              {product.name}
            </h1>
          </div>

          <p className="lead">{product.description}</p>

          <div>
            <span className="price-tag price-tag-lg">{formatCurrency(product.price)}</span>
          </div>

          <div className="buy-row">
            <div className="stepper" role="group" aria-label="Quantidade">
              <button
                type="button"
                className="icon-btn"
                aria-label="Diminuir quantidade"
                disabled={quantity <= 1}
                onClick={() => setQuantity((value) => Math.max(1, value - 1))}
              >
                <MinusIcon size={18} />
              </button>
              <input
                id="qty"
                type="number"
                inputMode="numeric"
                min={1}
                aria-label="Quantidade"
                value={quantity}
                onChange={(e) => setQuantity(Math.max(1, Number(e.target.value)))}
              />
              <button
                type="button"
                className="icon-btn"
                aria-label="Aumentar quantidade"
                onClick={() => setQuantity((value) => value + 1)}
              >
                <PlusIcon size={18} />
              </button>
            </div>
            <Button variant="primary" size="lg" disabled={adding || !product.inStock} onClick={handleAdd}>
              <CartIcon size={20} />
              {adding ? "Adicionando…" : product.inStock ? "Adicionar ao carrinho" : "Sem estoque"}
            </Button>
          </div>

          {error && <Notice tone="danger">{error}</Notice>}

          <p className="row muted" style={{ "--gap": "8px" } as React.CSSProperties}>
            <PinIcon size={18} />
            Retirada no balcão — Rua das Patinhas, 123
          </p>

          {dimensions && (
            <dl className="spec-list">
              <div>
                <dt>Peso</dt>
                <dd>{dimensions.weight} kg</dd>
              </div>
              <div>
                <dt>Altura</dt>
                <dd>{dimensions.height} cm</dd>
              </div>
              <div>
                <dt>Largura</dt>
                <dd>{dimensions.width} cm</dd>
              </div>
              <div>
                <dt>Comprimento</dt>
                <dd>{dimensions.length} cm</dd>
              </div>
            </dl>
          )}
        </div>
      </div>
    </div>
  );
}
