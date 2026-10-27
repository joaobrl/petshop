import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import * as authService from "../../services/authService";
import { ApiError } from "../../services/http";
import { TextField } from "../../components/ui/Field";
import { Button } from "../../components/ui/Button";

export function RegisterPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
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
      await login(form.email, form.password);
      navigate("/minha-conta/pets");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível criar a conta.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ maxWidth: 420, margin: "0 auto" }}>
      <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
        <h1 className="title">Criar conta</h1>
        <TextField
          label="Nome completo"
          value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          required
        />
        <TextField
          label="CPF"
          value={form.cpf}
          onChange={(e) => setForm({ ...form, cpf: e.target.value })}
          hint="Apenas números — 11 dígitos."
          required
        />
        <TextField
          label="E-mail"
          type="email"
          value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })}
          required
        />
        <TextField label="Telefone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} required />
        <TextField
          label="Senha"
          type="password"
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
          hint="Mínimo de 6 caracteres."
          required
        />
        <TextField
          label="Confirmar senha"
          type="password"
          value={form.confirmPassword}
          onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
          required
        />
        {error && (
          <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
            {error}
          </p>
        )}
        <Button variant="primary" type="submit" disabled={submitting}>
          {submitting ? "Criando…" : "Criar conta"}
        </Button>
      </form>
      <p className="caption" style={{ marginTop: "var(--space-4)", color: "var(--ink-muted)" }}>
        Já tem conta? <Link to="/entrar">Entrar</Link>
      </p>
    </div>
  );
}
