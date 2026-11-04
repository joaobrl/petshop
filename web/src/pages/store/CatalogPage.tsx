import { listProducts } from "../../services/productService";
import { useApi } from "../../hooks/useApi";
import { ErrorState } from "../../components/ErrorState";
import { ProductCard } from "../../components/store/ProductCard";
import { PawBackground } from "../../components/ui/PawBackground";

export function CatalogPage() {
  const { data: products, error, retry } = useApi(() => listProducts(), []);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-8)" }}>
      <PawBackground tint="var(--secondary-tint)">
        <div style={{ padding: "var(--space-8) var(--space-6)", maxWidth: 480 }}>
          <h1 className="display">Tudo que seu pet precisa</h1>
          <p className="body" style={{ marginTop: "var(--space-3)", color: "var(--ink-muted)" }}>
            Ração, acessórios e brinquedos com retirada na loja — e, se o seu pet
            precisar de um banho, a gente também agenda o serviço.
          </p>
        </div>
      </PawBackground>

      <div>
        <h2 className="title" style={{ marginBottom: "var(--space-4)" }}>
          Catálogo
        </h2>
        {error ? (
          <ErrorState message={error} onRetry={retry} />
        ) : !products ? (
          <p className="body">Carregando produtos…</p>
        ) : (
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))",
              gap: "var(--space-4)",
            }}
          >
            {products.map((product) => (
              <ProductCard key={product.id} product={product} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
