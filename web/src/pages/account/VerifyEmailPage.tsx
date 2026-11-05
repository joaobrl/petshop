import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { resendVerification, verifyEmail } from "../../services/authService";
import { ApiError } from "../../services/http";
import { TextField } from "../../components/ui/Field";
import { Button } from "../../components/ui/Button";

// The token is single-use: StrictMode (dev) runs effects twice, so the
// request is shared per token instead of being fired again.
const inflight = new Map<string, Promise<void>>();

function verifyOnce(token: string): Promise<void> {
  let promise = inflight.get(token);
  if (!promise) {
    promise = verifyEmail(token);
    inflight.set(token, promise);
  }
  return promise;
}

type Status = "verifying" | "success" | "error";

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");
  const { user, isAuthenticated, logout } = useAuth();
  const [status, setStatus] = useState<Status>(token ? "verifying" : "error");
  const [error, setError] = useState<string | null>(token ? null : "Link de verificação inválido.");

  useEffect(() => {
    if (!token) return;
    let active = true;
    verifyOnce(token)
      .then(() => {
        if (active) setStatus("success");
      })
      .catch((err) => {
        if (!active) return;
        setStatus("error");
        setError(err instanceof ApiError ? err.message : "Não foi possível confirmar o e-mail.");
      });
    return () => {
      active = false;
    };
  }, [token]);

  // The current JWT still says email_verified=false: a fresh login is needed (CA3).
  useEffect(() => {
    if (status === "success" && isAuthenticated) logout();
  }, [status, isAuthenticated, logout]);

  return (
    <div style={{ maxWidth: 420, margin: "0 auto", display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
      <h1 className="title">Confirmação de e-mail</h1>

      {status === "verifying" && <p className="body">Confirmando seu e-mail…</p>}

      {status === "success" && (
        <>
          <p className="body" style={{ color: "var(--success)", margin: 0 }}>
            E-mail confirmado. Entre novamente para continuar.
          </p>
          <Link to="/entrar" className="btn btn-primary">
            Entrar
          </Link>
        </>
      )}

      {status === "error" && (
        <>
          <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
            {error}
          </p>
          <ResendVerificationForm initialEmail={user?.email ?? ""} />
        </>
      )}
    </div>
  );
}

function ResendVerificationForm({ initialEmail }: { initialEmail: string }) {
  const [email, setEmail] = useState(initialEmail);
  const [submitting, setSubmitting] = useState(false);
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await resendVerification(email);
      setSent(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível reenviar o e-mail.");
    } finally {
      setSubmitting(false);
    }
  }

  if (sent) {
    return (
      <p className="body" style={{ margin: 0 }}>
        Se houver uma conta pendente de confirmação com esse e-mail, enviamos um novo link.
      </p>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
      <TextField label="E-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      {error && (
        <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
          {error}
        </p>
      )}
      <Button variant="secondary" type="submit" disabled={submitting}>
        {submitting ? "Enviando…" : "Reenviar e-mail de confirmação"}
      </Button>
    </form>
  );
}
