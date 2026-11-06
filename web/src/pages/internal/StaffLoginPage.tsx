import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { safeNext } from "../../services/authService";
import { Logo } from "../../components/ui/Logo";
import { Button } from "../../components/ui/Button";

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
    <div style={{ maxWidth: 360, margin: "0 auto", display: "flex", flexDirection: "column", gap: "var(--space-6)" }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <Logo height={56} />
      </div>
      <h1 className="title" style={{ textAlign: "center" }}>
        Painel interno
      </h1>
      {isCustomer && (
        <>
          <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
            Esta conta é de cliente, não de equipe.
          </p>
          <Button variant="primary" type="button" onClick={() => void logout()}>
            Sair e entrar com outra conta
          </Button>
        </>
      )}
      {!isCustomer && expired && !error && (
        <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
          Sua sessão expirou, entre novamente.
        </p>
      )}
      {!isCustomer && !expired && !error && (
        <p className="body" style={{ textAlign: "center", margin: 0 }}>
          Redirecionando para a tela de login...
        </p>
      )}
      {error && (
        <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
          {error}
        </p>
      )}
      {!isCustomer && (expired || error) && (
        <Button variant="primary" type="button" onClick={goToKeycloak}>
          Entrar
        </Button>
      )}
      <p className="caption" style={{ color: "var(--ink-muted)", textAlign: "center" }}>
        Use a conta de administrador do bootstrap do customer-management (BOOTSTRAP_ADMIN_*) ou uma
        conta de equipe cadastrada por um admin (a senha é definida pelo e-mail do Keycloak).
      </p>
    </div>
  );
}
