import { Link, NavLink, Outlet } from "react-router-dom";
import { Brand } from "../ui/Logo";
import { CalendarIcon, CartIcon, HomeIcon, LogoutIcon, UsersIcon } from "../ui/Icon";
import { useAuth } from "../../context/AuthContext";
import { ROLE_LABEL } from "../../types/staff";
import { ACCOUNT_CONSOLE_URL } from "../../services/oidc";

export function InternalLayout() {
  const { role, user, logout } = useAuth();

  return (
    <div className="internal">
      <aside className="internal-side">
        <Link to="/interno" aria-label="Painel interno" style={{ textDecoration: "none" }}>
          <Brand light />
        </Link>
        <nav className="internal-nav" aria-label="Painel interno">
          <NavLink to="/interno" className="internal-link" end>
            <HomeIcon size={20} />
            Painel
          </NavLink>
          <NavLink to="/interno/pedidos" className="internal-link">
            <CartIcon size={20} />
            Pedidos
          </NavLink>
          <NavLink to="/interno/agendamentos" className="internal-link">
            <CalendarIcon size={20} />
            Agendamentos
          </NavLink>
          <NavLink to="/interno/equipe" className="internal-link">
            <UsersIcon size={20} />
            Equipe
          </NavLink>
        </nav>
        <div className="internal-user">
          <span className="internal-user-text">
            <strong>{user?.name ?? "Equipe"}</strong>
            {role ? ROLE_LABEL[role] : ""}
            {/* Dados, senha e sessões: Account Console do Keycloak (SDD-024 F3). */}
            <a href={ACCOUNT_CONSOLE_URL} className="internal-account">
              Minha conta / alterar senha
            </a>
          </span>
          <button
            type="button"
            className="icon-btn"
            aria-label="Sair"
            title="Sair"
            onClick={() => void logout()}
          >
            <LogoutIcon />
          </button>
        </div>
      </aside>
      <main className="internal-main">
        <Outlet />
      </main>
    </div>
  );
}
