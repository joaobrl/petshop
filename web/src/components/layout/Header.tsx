import { NavLink, useNavigate } from "react-router-dom";
import { Logo } from "../ui/Logo";
import { CartIcon } from "../ui/Icon";
import { useCart } from "../../context/CartContext";
import { useAuth } from "../../context/AuthContext";

const navLinkStyle = ({ isActive }: { isActive: boolean }) => ({
  fontFamily: "var(--font-sans)",
  fontWeight: 600,
  fontSize: 14,
  textDecoration: "none",
  color: isActive ? "var(--ink)" : "var(--ink-muted)",
});

export function Header() {
  const { totalItems } = useCart();
  const { isAuthenticated, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <header
      style={{
        position: "sticky",
        top: 0,
        zIndex: 10,
        background: "var(--surface-raised)",
        borderBottom: "1px solid var(--border)",
      }}
    >
      <div
        className="container"
        style={{
          display: "flex",
          alignItems: "center",
          gap: "var(--space-6)",
          height: 72,
        }}
      >
        <NavLink to="/" style={{ display: "flex", alignItems: "center" }}>
          <Logo height={40} />
        </NavLink>

        <nav style={{ display: "flex", gap: "var(--space-6)", flex: 1 }}>
          <NavLink to="/" style={navLinkStyle} end>
            Catálogo
          </NavLink>
          <NavLink to="/agendar" style={navLinkStyle}>
            Agendar serviço
          </NavLink>
          {isAuthenticated && (
            <>
              <NavLink to="/minha-conta/pedidos" style={navLinkStyle}>
                Meus pedidos
              </NavLink>
              <NavLink to="/minha-conta/agendamentos" style={navLinkStyle}>
                Meus agendamentos
              </NavLink>
              <NavLink to="/minha-conta/pets" style={navLinkStyle}>
                Meus pets
              </NavLink>
            </>
          )}
        </nav>

        <div style={{ display: "flex", alignItems: "center", gap: "var(--space-4)" }}>
          {isAuthenticated ? (
            <button
              className="btn btn-outline btn-sm"
              onClick={() => {
                logout();
                navigate("/");
              }}
            >
              Sair
            </button>
          ) : (
            <NavLink to="/entrar" className="btn btn-outline btn-sm" style={{ textDecoration: "none" }}>
              Entrar
            </NavLink>
          )}

          <NavLink
            to="/carrinho"
            style={{
              position: "relative",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              width: 44,
              height: 44,
              borderRadius: "var(--radius-sm)",
              border: "1px solid var(--border)",
              background: "var(--surface)",
            }}
            aria-label="Carrinho"
          >
            <CartIcon />
            {totalItems > 0 && (
              <span
                style={{
                  position: "absolute",
                  top: -6,
                  right: -6,
                  minWidth: 20,
                  height: 20,
                  borderRadius: "var(--radius-full)",
                  background: "var(--primary)",
                  color: "var(--ink)",
                  fontSize: 11,
                  fontWeight: 700,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  padding: "0 4px",
                }}
              >
                {totalItems}
              </span>
            )}
          </NavLink>
        </div>
      </div>
    </header>
  );
}
