import { useEffect, useState } from "react";
import { Link, NavLink, useLocation } from "react-router-dom";
import { ACCOUNT_CONSOLE_URL } from "../../services/oidc";
import { Brand } from "../ui/Logo";
import { CartIcon, CloseIcon, MenuIcon } from "../ui/Icon";
import { useCart } from "../../context/CartContext";
import { useAuth } from "../../context/AuthContext";

const SHOP_LINKS = [
  { to: "/", label: "Loja", end: true },
  { to: "/agendar", label: "Banho & tosa", end: false },
];

const ACCOUNT_LINKS = [
  { to: "/minha-conta/pedidos", label: "Meus pedidos" },
  { to: "/minha-conta/agendamentos", label: "Agendamentos" },
  { to: "/minha-conta/pets", label: "Meus pets" },
];

export function Header() {
  const { totalItems } = useCart();
  const { isAuthenticated, user, logout } = useAuth();
  const location = useLocation();
  // The drawer remembers which navigation it was opened on (location.key is
  // unique per navigation), so it closes itself whenever the route changes.
  const [openAt, setOpenAt] = useState<string | null>(null);
  const open = openAt === location.key;
  const setOpen = (value: boolean) => setOpenAt(value ? location.key : null);

  useEffect(() => {
    if (!open) return;
    function onKey(event: KeyboardEvent) {
      if (event.key === "Escape") setOpenAt(null);
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open]);

  function handleLogout() {
    setOpen(false);
    void logout();
  }

  const firstName = user?.name?.split(" ")[0];

  return (
    <header className={open ? "header is-open" : "header"}>
      <div className="container header-bar">
        <Link to="/" aria-label="WebCommerce PetShop — início" style={{ textDecoration: "none" }}>
          <Brand />
        </Link>

        <nav className="nav" aria-label="Principal">
          {SHOP_LINKS.map((link) => (
            <NavLink key={link.to} to={link.to} end={link.end} className="nav-link">
              {link.label}
            </NavLink>
          ))}
          {isAuthenticated && (
            <>
              <span className="nav-divider" aria-hidden="true" />
              {ACCOUNT_LINKS.map((link) => (
                <NavLink key={link.to} to={link.to} className="nav-link">
                  {link.label}
                </NavLink>
              ))}
              {/* Dados, senha e sessões: Account Console do Keycloak (SDD-024 F3). */}
              <a href={ACCOUNT_CONSOLE_URL} className="nav-link" title="Dados, senha e sessões">
                Minha conta
              </a>
            </>
          )}
        </nav>

        <div className="header-actions">
          {isAuthenticated && firstName && <span className="header-hello">oi, {firstName}!</span>}
          {isAuthenticated ? (
            <button type="button" className="btn btn-outline btn-sm header-auth" onClick={handleLogout}>
              Sair
            </button>
          ) : (
            <Link to="/entrar" className="btn btn-outline btn-sm header-auth">
              Entrar
            </Link>
          )}

          <Link
            to="/carrinho"
            className="cart-btn"
            aria-label={totalItems > 0 ? `Carrinho, ${totalItems} ${totalItems === 1 ? "item" : "itens"}` : "Carrinho"}
          >
            <CartIcon />
            {totalItems > 0 && <span className="cart-count">{totalItems}</span>}
          </Link>

          <button
            type="button"
            className="icon-btn menu-btn"
            aria-label={open ? "Fechar menu" : "Abrir menu"}
            aria-expanded={open}
            aria-controls="menu-principal"
            onClick={() => setOpen(!open)}
          >
            {open ? <CloseIcon /> : <MenuIcon />}
          </button>
        </div>
      </div>

      <div className="nav-panel" id="menu-principal">
        <nav className="container nav-panel-inner" aria-label="Menu">
          {SHOP_LINKS.map((link) => (
            <NavLink key={link.to} to={link.to} end={link.end} className="panel-link">
              {link.label}
            </NavLink>
          ))}
          {isAuthenticated ? (
            <>
              <span className="panel-label">{firstName ? `a conta de ${firstName}` : "sua conta"}</span>
              {ACCOUNT_LINKS.map((link) => (
                <NavLink key={link.to} to={link.to} className="panel-link">
                  {link.label}
                </NavLink>
              ))}
              <a href={ACCOUNT_CONSOLE_URL} className="panel-link">
                Minha conta / alterar senha
              </a>
              <button type="button" className="btn btn-outline btn-block" onClick={handleLogout}>
                Sair
              </button>
            </>
          ) : (
            <>
              <Link to="/entrar" className="btn btn-primary btn-block">
                Entrar
              </Link>
              <Link to="/cadastro" className="btn btn-outline btn-block">
                Criar conta
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  );
}
