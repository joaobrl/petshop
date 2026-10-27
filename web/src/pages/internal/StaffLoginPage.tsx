import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { ApiError } from "../../services/http";
import { Logo } from "../../components/ui/Logo";
import { TextField } from "../../components/ui/Field";
import { Button } from "../../components/ui/Button";
import { ChangePasswordForm } from "../../components/account/ChangePasswordForm";

export function StaffLoginPage() {
  const { login, mustChangePassword, clearMustChangePassword, logout } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("admin@petshop.local");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const response = await login(email, password);
      if (response.role === "CUSTOMER") {
        logout();
        setError("Esta conta é de cliente, não de equipe.");
        return;
      }
      if (!response.mustChangePassword) navigate("/interno");
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
          navigate("/interno");
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
          Painel interno
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
        <p className="caption" style={{ color: "var(--ink-muted)", textAlign: "center" }}>
          Use a conta de administrador criada no bootstrap do customer-management (ver .env /
          BOOTSTRAP_ADMIN_*) ou uma conta de equipe cadastrada por um admin.
        </p>
      </form>
    </div>
  );
}
