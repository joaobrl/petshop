import { useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import * as authService from "../../services/authService";
import { ApiError } from "../../services/http";
import { TextField } from "../../components/ui/Field";
import { Button } from "../../components/ui/Button";
import { Notice } from "../../components/ui/Feedback";
import { AuthShell } from "../../components/account/AuthShell";

export function RegisterPage() {
  const { login } = useAuth();
  const [form, setForm] = useState({ name: "", cpf: "", email: "", phone: "", password: "", confirmPassword: "" });
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await authService.registerCustomer({
        ...form,
        cpf: form.cpf.replace(/\D/g, ""),
      });
      // Sem auto-login por senha (SDD-024 F3): segue para o login do Keycloak, com o
      // e-mail preenchido.
      await login({ next: "/minha-conta/pets", loginHint: form.email });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível criar a conta.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="container page">
      <AuthShell
        greeting="Puxa uma cadeira, a casa é sua."
        photo="/images/avatars/gato-siames.webp"
        caption="avaliando o novo cliente"
        tile="var(--snout)"
      >
        <div>
          <span className="eyebrow">leva um minutinho</span>
          <h1 className="title">Criar conta</h1>
        </div>
        <form onSubmit={handleSubmit} className="form">
          <TextField
            label="Nome completo"
            autoComplete="name"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
            required
          />
          <div className="field-row">
            <TextField
              label="CPF"
              inputMode="numeric"
              value={form.cpf}
              onChange={(e) => setForm({ ...form, cpf: e.target.value })}
              hint="Apenas números — 11 dígitos."
              required
            />
            <TextField
              label="Telefone"
              type="tel"
              autoComplete="tel"
              value={form.phone}
              onChange={(e) => setForm({ ...form, phone: e.target.value })}
              required
            />
          </div>
          <TextField
            label="E-mail"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            required
          />
          <div className="field-row">
            <TextField
              label="Senha"
              type="password"
              autoComplete="new-password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              hint="Mínimo de 6 caracteres."
              required
            />
            <TextField
              label="Confirmar senha"
              type="password"
              autoComplete="new-password"
              value={form.confirmPassword}
              onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
              required
            />
          </div>
          {error && <Notice tone="danger">{error}</Notice>}
          <Button variant="primary" type="submit" size="lg" disabled={submitting} block>
            {submitting ? "Criando…" : "Criar conta"}
          </Button>
        </form>
        <p className="auth-foot">
          Já tem conta?{" "}
          <Link to="/entrar" className="link">
            Entrar
          </Link>
        </p>
      </AuthShell>
    </div>
  );
}
