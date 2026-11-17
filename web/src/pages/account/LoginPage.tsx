import { useEffect, useState } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { safeNext } from "../../services/authService";
import { Button } from "../../components/ui/Button";
import { Loading, Notice } from "../../components/ui/Feedback";
import { AuthShell } from "../../components/account/AuthShell";

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
    <div className="container page">
      <AuthShell
        greeting="Que bom ver você de novo."
        photo="/images/avatars/golden-retriever.webp"
        caption="já estava na porta esperando"
      >
        <div>
          <span className="eyebrow">bem-vindo de volta</span>
          <h1 className="title">Entrar</h1>
        </div>
        <div className="form">
          {expired && !error && <Notice tone="warning">Sua sessão expirou, entre novamente.</Notice>}
          {!expired && !error && <Loading>Abrindo a tela de login…</Loading>}
          {error && <Notice tone="danger">{error}</Notice>}
          {(expired || error) && (
            <Button variant="primary" size="lg" block onClick={goToKeycloak}>
              Entrar
            </Button>
          )}
        </div>
        <p className="auth-foot">
          Ainda não tem conta?{" "}
          <Link to="/cadastro" className="link">
            Criar conta
          </Link>
        </p>
      </AuthShell>
    </div>
  );
}
