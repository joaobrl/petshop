import { useState } from "react";
import { useLocation } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { isUnverifiedCustomer, resendVerification } from "../../services/authService";
import { ApiError } from "../../services/http";
import { Button } from "../ui/Button";

// Discreet banner for a logged-in customer whose JWT says email_verified=false
// (SDD-020): browsing and cart work, checkout/booking answer 403.
export function EmailVerificationNotice() {
  const { user } = useAuth();
  const location = useLocation();
  const [status, setStatus] = useState<"idle" | "sending" | "sent" | "error">("idle");
  const [error, setError] = useState<string | null>(null);

  if (!isUnverifiedCustomer(user) || location.pathname === "/verify-email") return null;

  async function handleResend() {
    if (!user) return;
    setStatus("sending");
    setError(null);
    try {
      await resendVerification(user.email);
      setStatus("sent");
    } catch (err) {
      setStatus("error");
      setError(err instanceof ApiError ? err.message : "Não foi possível reenviar o e-mail.");
    }
  }

  return (
    <div
      role="status"
      style={{
        background: "var(--warning-tint)",
        color: "var(--ink)",
        borderBottom: "1px solid var(--border)",
      }}
    >
      <div
        className="container"
        style={{
          display: "flex",
          flexWrap: "wrap",
          alignItems: "center",
          justifyContent: "space-between",
          gap: "var(--space-3)",
          padding: "var(--space-3) var(--space-6)",
        }}
      >
        <p className="caption" style={{ margin: 0 }}>
          {status === "sent"
            ? `Enviamos um novo link de confirmação para ${user?.email}.`
            : "Confirme seu e-mail para fechar pedidos e agendamentos. Verifique sua caixa de entrada."}
          {error && <span style={{ color: "var(--danger)", marginLeft: "var(--space-2)" }}>{error}</span>}
        </p>
        {status !== "sent" && (
          <Button variant="outline" size="sm" type="button" onClick={handleResend} disabled={status === "sending"}>
            {status === "sending" ? "Enviando…" : "Reenviar e-mail"}
          </Button>
        )}
      </div>
    </div>
  );
}
