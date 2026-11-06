import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { safeNext } from "../../services/authService";

// redirect_uri do client "web" (SDD-024 F3): troca o authorization code (PKCE) pelos
// tokens e volta para a página de origem.
export function AuthCallbackPage() {
  const { completeLogin } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    completeLogin()
      .then((next) => {
        if (active) navigate(safeNext(next) ?? "/", { replace: true });
      })
      .catch(() => {
        if (active) setError("Não foi possível concluir o login.");
      });
    return () => {
      active = false;
    };
  }, [completeLogin, navigate]);

  return (
    <div style={{ maxWidth: 420, margin: "0 auto", display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
      {error ? (
        <>
          <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
            {error}
          </p>
          <Link to="/entrar" className="btn btn-primary">
            Tentar de novo
          </Link>
        </>
      ) : (
        <p className="body">Entrando...</p>
      )}
    </div>
  );
}
