import { useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { safeNext } from "../../services/authService";
import { Brand } from "../../components/ui/Logo";
import { Button } from "../../components/ui/Button";
import { Loading, Notice } from "../../components/ui/Feedback";
import { AuthShell } from "../../components/account/AuthShell";

// Same Keycloak login as the store (SDD-024 F3); only the destination differs.
export function StaffLoginPage() {
  const { login, logout, role, isAuthenticated, initializing } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const expired = searchParams.get("expired") === "1";
  const destination = safeNext(searchParams.get("next")) ?? "/interno";
  const [error, setError] = useState<string | null>(null);
  const isCustomer = isAuthenticated && role === "CUSTOMER";

  function goToKeycloak() {
    setError(null);
    login({ next: destination }).catch(() => setError("Não foi possível abrir a tela de login. Tente novamente."));
  }

  useEffect(() => {
    if (initializing || expired) return;
    if (isAuthenticated) {
      // A customer account never enters the panel; no auto-redirect (it would loop via SSO).
      if (role !== "CUSTOMER") navigate(destination, { replace: true });
      return;
    }
    login({ next: destination }).catch(() => setError("Não foi possível abrir a tela de login. Tente novamente."));
  }, [initializing, expired, isAuthenticated, role, login, navigate, destination]);

  return (
    <div className="standalone">
      <div className="awning" aria-hidden="true" />
      <main className="container standalone-main">
        <AuthShell
          greeting="Bom trabalho hoje, equipe."
          photo="/images/avatars/gato-preto.webp"
          caption="supervisor do turno"
          tile="var(--bath)"
        >
          <Link to="/" aria-label="Voltar para a loja" style={{ textDecoration: "none", alignSelf: "flex-start" }}>
            <Brand />
          </Link>
          <div>
            <span className="eyebrow">só para a equipe</span>
            <h1 className="title">Painel interno</h1>
          </div>
          <div className="form">
            {isCustomer && (
              <>
                <Notice tone="danger">Esta conta é de cliente, não de equipe.</Notice>
                <Button variant="primary" size="lg" block onClick={() => void logout()}>
                  Sair e entrar com outra conta
                </Button>
              </>
            )}
            {!isCustomer && expired && !error && <Notice tone="warning">Sua sessão expirou, entre novamente.</Notice>}
            {!isCustomer && !expired && !error && <Loading>Abrindo a tela de login…</Loading>}
            {error && <Notice tone="danger">{error}</Notice>}
            {!isCustomer && (expired || error) && (
              <Button variant="primary" size="lg" block onClick={goToKeycloak}>
                Entrar
              </Button>
            )}
          </div>
          <p className="caption muted">
            Use a conta de administrador do bootstrap do customer-management (BOOTSTRAP_ADMIN_*) ou uma conta de
            equipe cadastrada por um admin (a senha é definida pelo e-mail do Keycloak).
          </p>
        </AuthShell>
      </main>
    </div>
  );
}
