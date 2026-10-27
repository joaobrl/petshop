import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { Logo } from "../ui/Logo";
import { useAuth } from "../../context/AuthContext";
import { ROLE_LABEL } from "../../types/staff";

const linkStyle = ({ isActive }: { isActive: boolean }) => ({
  display: "block",
  padding: "var(--space-2) var(--space-3)",
  borderRadius: "var(--radius-sm)",
  textDecoration: "none",
  fontFamily: "var(--font-sans)",
  fontWeight: 600,
  fontSize: 14,
  color: isActive ? "var(--ink)" : "var(--ink-muted)",
  background: isActive ? "var(--secondary-tint)" : "transparent",
});

export function InternalLayout() {
  const { role, logout } = useAuth();
  const navigate = useNavigate();

  return (
    <div style={{ display: "grid", gridTemplateColumns: "220px 1fr", minHeight: "100%" }}>
      <aside
        style={{
          background: "var(--surface-raised)",
          borderRight: "1px solid var(--border)",
          padding: "var(--space-6) var(--space-4)",
          display: "flex",
          flexDirection: "column",
          gap: "var(--space-6)",
        }}
      >
        <Logo height={32} />
        <nav style={{ display: "flex", flexDirection: "column", gap: "var(--space-1)" }}>
          <NavLink to="/interno" style={linkStyle} end>
            Painel
          </NavLink>
          <NavLink to="/interno/pedidos" style={linkStyle}>
            Pedidos
          </NavLink>
          <NavLink to="/interno/agendamentos" style={linkStyle}>
            Agendamentos
          </NavLink>
          <NavLink to="/interno/equipe" style={linkStyle}>
            Equipe
          </NavLink>
        </nav>
        <div style={{ marginTop: "auto", display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
          <span className="caption" style={{ color: "var(--ink-muted)" }}>
            Logado como {role ? ROLE_LABEL[role] : ""}
          </span>
          <button
            className="btn btn-outline btn-sm"
            onClick={() => {
              logout();
              navigate("/");
            }}
          >
            Sair
          </button>
        </div>
      </aside>
      <main style={{ padding: "var(--space-8) var(--space-6)" }}>
        <Outlet />
      </main>
    </div>
  );
}
