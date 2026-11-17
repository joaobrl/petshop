import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { resendVerification, verifyEmail } from "../../services/authService";
import { ApiError } from "../../services/http";
import { TextField } from "../../components/ui/Field";
import { Button } from "../../components/ui/Button";
import { Loading, Notice } from "../../components/ui/Feedback";

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
  const { user, isAuthenticated, refreshSession } = useAuth();
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

  // The current token still says email_verified=false. The backend already set it to
  // true in Keycloak (SDD-024 F3), so a renewed token carries the new claim (CA3) —
  // no logout needed.
  const refreshed = useRef(false);
  useEffect(() => {
    if (status !== "success" || !isAuthenticated || refreshed.current) return;
    refreshed.current = true;
    void refreshSession();
  }, [status, isAuthenticated, refreshSession]);

  return (
    <div className="container page">
      <div className="card stack" style={{ maxWidth: 480, margin: "0 auto" }}>
        <div>
          <span className="eyebrow">só para ter certeza que é você</span>
          <h1 className="title">Confirmação de e-mail</h1>
        </div>

        {status === "verifying" && <Loading>Confirmando seu e-mail…</Loading>}

        {status === "success" && (
          <>
            <Notice tone="success">
              {isAuthenticated ? "E-mail confirmado." : "E-mail confirmado. Entre para continuar."}
            </Notice>
            {isAuthenticated ? (
              <Link to="/" className="btn btn-primary">
                Continuar
              </Link>
            ) : (
              <Link to="/entrar" className="btn btn-primary">
                Entrar
              </Link>
            )}
          </>
        )}

        {status === "error" && (
          <>
            <Notice tone="danger">{error}</Notice>
            <ResendVerificationForm initialEmail={user?.email ?? ""} />
          </>
        )}
      </div>
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
      <Notice tone="success">
        Se houver uma conta pendente de confirmação com esse e-mail, enviamos um novo link.
      </Notice>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="form">
      <TextField label="E-mail" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      {error && <Notice tone="danger">{error}</Notice>}
      <Button variant="secondary" type="submit" disabled={submitting}>
        {submitting ? "Enviando…" : "Reenviar e-mail de confirmação"}
      </Button>
    </form>
  );
}
