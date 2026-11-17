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
    <div className="verify-strip" role="status">
      <div className="container verify-strip-inner">
        <p>
          {status === "sent"
            ? `Enviamos um novo link de confirmação para ${user?.email}.`
            : "Confirme seu e-mail para fechar pedidos e agendamentos. Verifique sua caixa de entrada."}
          {error && <span className="verify-strip-error"> {error}</span>}
        </p>
        {status !== "sent" && (
          <Button variant="outline" size="sm" onClick={handleResend} disabled={status === "sending"}>
            {status === "sending" ? "Enviando…" : "Reenviar e-mail"}
          </Button>
        )}
      </div>
    </div>
  );
}
