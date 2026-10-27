import { useState } from "react";
import { useLocation, useNavigate, Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { ApiError } from "../../services/http";
import { TextField } from "../../components/ui/Field";
import { Button } from "../../components/ui/Button";
import { Logo } from "../../components/ui/Logo";
import { ChangePasswordForm } from "../../components/account/ChangePasswordForm";

export function LoginPage() {
  const { login, mustChangePassword, clearMustChangePassword } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function goToDestination() {
    const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? "/minha-conta/pedidos";
    navigate(from, { replace: true });
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const response = await login(email, password);
      if (!response.mustChangePassword) goToDestination();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível entrar.");
    } finally {
      setSubmitting(false);
    }
  }

  if (mustChangePassword) {
    return (
      <ChangePasswordForm
        email={email}
        currentPassword={password}
        onDone={() => {
          clearMustChangePassword();
          goToDestination();
        }}
      />
    );
  }

  return (
    <div style={{ maxWidth: 360, margin: "0 auto", display: "flex", flexDirection: "column", gap: "var(--space-6)" }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <Logo height={56} />
      </div>
      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
        <h1 className="title" style={{ textAlign: "center" }}>
          Entrar
        </h1>
        <TextField label="E-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        <TextField label="Senha" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error && (
          <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
            {error}
          </p>
        )}
        <Button variant="primary" type="submit" disabled={submitting}>
          {submitting ? "Entrando…" : "Entrar"}
        </Button>
      </form>
      <p className="caption" style={{ textAlign: "center", color: "var(--ink-muted)" }}>
        Ainda não tem conta? <Link to="/cadastro">Criar conta</Link>
      </p>
    </div>
  );
}
