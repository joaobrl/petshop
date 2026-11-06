import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { safeNext } from "../../services/authService";
import { Button } from "../../components/ui/Button";
import { Logo } from "../../components/ui/Logo";

// Login is the Keycloak page (SDD-024 F3): this route only redirects there. "Manter
// conectado" (Remember Me) and "Esqueci a senha" are on the Keycloak screen.
export function LoginPage() {
  const { login, isAuthenticated, initializing } = useAuth();
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const expired = searchParams.get("expired") === "1";
  const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname;
  const next = safeNext(searchParams.get("next")) ?? safeNext(from) ?? "/minha-conta/pedidos";
  const [error, setError] = useState<string | null>(null);

  function goToKeycloak() {
    setError(null);
    login({ next }).catch(() => setError("Não foi possível abrir a tela de login. Tente novamente."));
  }

  // Straight to Keycloak, except after an expired session (the user sees why first).
  useEffect(() => {
    if (initializing || expired) return;
    if (isAuthenticated) {
      navigate(next, { replace: true });
      return;
    }
    login({ next }).catch(() => setError("Não foi possível abrir a tela de login. Tente novamente."));
  }, [initializing, expired, isAuthenticated, login, navigate, next]);

  return (
    <div style={{ maxWidth: 360, margin: "0 auto", display: "flex", flexDirection: "column", gap: "var(--space-6)" }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <Logo height={56} />
      </div>
      <h1 className="title" style={{ textAlign: "center" }}>
        Entrar
      </h1>
      {expired && !error && (
        <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
          Sua sessão expirou, entre novamente.
        </p>
      )}
      {!expired && !error && (
        <p className="body" style={{ textAlign: "center", margin: 0 }}>
          Redirecionando para a tela de login...
        </p>
      )}
      {error && (
        <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
          {error}
        </p>
      )}
      {(expired || error) && (
        <Button variant="primary" type="button" onClick={goToKeycloak}>
          Entrar
        </Button>
      )}
      <p className="caption" style={{ textAlign: "center", color: "var(--ink-muted)" }}>
        Ainda não tem conta? <Link to="/cadastro">Criar conta</Link>
      </p>
    </div>
  );
}
