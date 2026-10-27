import { useState } from "react";
import * as authService from "../../services/authService";
import { ApiError } from "../../services/http";
import { TextField } from "../ui/Field";
import { Button } from "../ui/Button";
import { Logo } from "../ui/Logo";

interface ChangePasswordFormProps {
  email: string;
  currentPassword: string;
  onDone: () => void;
}

// Shown after login when the backend flags mustChangePassword — the
// bootstrap admin and every freshly created staff account (initial
// password = their CPF) hit this on first login.
export function ChangePasswordForm({ email, currentPassword, onDone }: ChangePasswordFormProps) {
  const [newPassword, setNewPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await authService.changePassword(email, currentPassword, newPassword);
      onDone();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível trocar a senha.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ maxWidth: 360, margin: "0 auto", display: "flex", flexDirection: "column", gap: "var(--space-6)" }}>
      <div style={{ display: "flex", justifyContent: "center" }}>
        <Logo height={56} />
      </div>
      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
        <h1 className="title" style={{ textAlign: "center" }}>
          Troque sua senha
        </h1>
        <p className="body" style={{ color: "var(--ink-muted)", textAlign: "center" }}>
          Esta conta precisa trocar a senha de primeiro acesso antes de continuar.
        </p>
        <TextField
          label="Nova senha"
          type="password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          required
          hint="Mínimo de 6 caracteres."
        />
        {error && (
          <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
            {error}
          </p>
        )}
        <Button variant="primary" type="submit" disabled={submitting}>
          {submitting ? "Salvando…" : "Trocar senha e continuar"}
        </Button>
      </form>
    </div>
  );
}
