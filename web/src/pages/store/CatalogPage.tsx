import { useEffect, useMemo, useState } from "react";
import type { CSSProperties } from "react";
import { Link } from "react-router-dom";
import type { Product } from "../../types/product";
import type { ServiceType } from "../../types/booking";
import { SERVICE_TYPE_LABEL } from "../../types/booking";
import { listProducts } from "../../services/productService";
import { ProductCard } from "../../components/store/ProductCard";
import { Polaroid } from "../../components/ui/Polaroid";
import { PawTrail } from "../../components/ui/PawTrail";
import { EmptyState, Loading } from "../../components/ui/Feedback";
import { Button } from "../../components/ui/Button";
import { ArrowRightIcon, CalendarIcon, CartIcon, PinIcon, SearchIcon, ServiceIcon } from "../../components/ui/Icon";
import { SERVICE_BLURB, SERVICE_TILE } from "../../utils/serviceInfo";

const FEATURED_SERVICES: ServiceType[] = ["BANHO", "TOSAGEM", "CONSULTA_VETERINARIA"];

function normalize(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase();
}

export function CatalogPage() {
  const [products, setProducts] = useState<Product[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [query, setQuery] = useState("");
  const [onlyInStock, setOnlyInStock] = useState(false);

  function load() {
    listProducts()
      .then(setProducts)
      .catch(() => setFailed(true));
  }

  useEffect(load, []);

  function retry() {
    setFailed(false);
    load();
  }

  const visible = useMemo(() => {
    if (!products) return [];
    const needle = normalize(query.trim());
    return products.filter(
      (product) =>
        (!onlyInStock || product.inStock) &&
        (needle === "" || normalize(`${product.name} ${product.description ?? ""}`).includes(needle)),
    );
  }, [products, query, onlyInStock]);

  return (
    <>
      <section className="hero">
        <div className="container hero-grid">
          <div className="hero-copy">
            <span className="eyebrow">entra, a casa é sua (e do seu bicho)</span>
            <h1 className="display">
              Aqui todo mundo é recebido com <mark>festa</mark>.
            </h1>
            <p className="lead">
              Ração, brinquedos e acessórios para retirar no balcão — e banho, tosa e veterinário com hora marcada,
              para ninguém ficar esperando de coleira na mão.
            </p>
            <div className="hero-actions">
              <a href="#prateleira" className="btn btn-primary btn-lg">
                Ver a prateleira
              </a>
              <Link to="/agendar" className="btn btn-outline btn-lg">
                <CalendarIcon size={20} />
                Marcar um horário
              </Link>
            </div>
            <ul className="hero-facts">
              <li>
                <PinIcon size={18} />
                Rua das Patinhas, 123
              </li>
              <li>
                <CartIcon size={18} />
                Compre aqui, retire na loja
              </li>
            </ul>
          </div>

          <div className="hero-photos" aria-hidden="true">
            <Polaroid src="/images/avatars/shiba-inu.webp" caption="Paçoca" tilt={-6} eager />
            <Polaroid
              src="/images/avatars/gato-laranja.webp"
              caption="Tofu (sem banho!)"
              tilt={3}
              tile="var(--bath-tint)"
              eager
            />
            <Polaroid
              src="/images/avatars/buldogue-frances.webp"
              caption="Bolota"
              tilt={7}
              tile="var(--snout-tint)"
              eager
            />
            <span className="hero-scribble">a turma da casa</span>
          </div>
        </div>
        <PawTrail />
      </section>

      <section className="band" aria-labelledby="servicos-titulo">
        <div className="container">
          <div className="band-head">
            <div>
              <span className="eyebrow">com hora marcada</span>
              <h2 className="title" id="servicos-titulo">
                Dia de spa (ou de consulta)
              </h2>
            </div>
            <Link to="/agendar" className="link">
              Ver todos os serviços
            </Link>
          </div>
          <div className="service-grid">
            {FEATURED_SERVICES.map((type) => (
              <Link key={type} to={`/agendar?servico=${type}`} className="service-card">
                <span className="service-icon" style={{ "--tile": SERVICE_TILE[type] } as CSSProperties}>
                  <ServiceIcon type={type} size={30} />
                </span>
                <h3 className="heading">{SERVICE_TYPE_LABEL[type]}</h3>
                <p>{SERVICE_BLURB[type]}</p>
                <span className="service-go">
                  Agendar <ArrowRightIcon size={18} />
                </span>
              </Link>
            ))}
          </div>
        </div>
      </section>

      <section className="shelf container" id="prateleira" aria-labelledby="prateleira-titulo">
        <header className="page-header">
          <div>
            <span className="eyebrow">direto do balcão</span>
            <h2 className="title" id="prateleira-titulo">
              A prateleira
            </h2>
          </div>
          {products && products.length > 0 && (
            <p className="muted" aria-live="polite">
              {visible.length} de {products.length} {products.length === 1 ? "produto" : "produtos"}
            </p>
          )}
        </header>

        {failed ? (
          <EmptyState title="A prateleira não carregou" note="deve ter sido o gato que derrubou o cabo…">
            <p className="muted">Não conseguimos falar com a loja agora. Tente de novo em instantes.</p>
            <Button variant="outline" onClick={retry}>
              Tentar de novo
            </Button>
          </EmptyState>
        ) : !products ? (
          <Loading>Arrumando a prateleira…</Loading>
        ) : products.length === 0 ? (
          <EmptyState title="A prateleira está vazia" note="o caminhão de ração já está chegando" />
        ) : (
          <>
            <div className="shelf-tools">
              <label className="search">
                <span className="sr-only">Buscar produto</span>
                <SearchIcon size={20} />
                <input
                  type="search"
                  placeholder="Procurar ração, coleira, brinquedo…"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                />
              </label>
              <label className="check">
                <input type="checkbox" checked={onlyInStock} onChange={(e) => setOnlyInStock(e.target.checked)} />
                Só o que tem em estoque
              </label>
            </div>

            {visible.length === 0 ? (
              <EmptyState title="Nada por aqui com esse nome" note="farejamos tudo e não achamos">
                <Button
                  variant="outline"
                  onClick={() => {
                    setQuery("");
                    setOnlyInStock(false);
                  }}
                >
                  Limpar a busca
                </Button>
              </EmptyState>
            ) : (
              <div className="product-grid">
                {visible.map((product) => (
                  <ProductCard key={product.id} product={product} />
                ))}
              </div>
            )}
          </>
        )}
      </section>
    </>
  );
}
