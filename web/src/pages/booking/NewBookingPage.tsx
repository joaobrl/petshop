import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import * as customerService from "../../services/customerService";
import * as bookingService from "../../services/bookingService";
import { ApiError } from "../../services/http";
import type { Customer } from "../../types/customer";
import type { ServiceType } from "../../types/booking";
import { SERVICE_TYPE_LABEL, formatBookingDateTime } from "../../types/booking";
import { Button } from "../../components/ui/Button";
import { SelectField, TextField } from "../../components/ui/Field";
import { PawBackground } from "../../components/ui/PawBackground";

const SERVICE_TYPES = Object.keys(SERVICE_TYPE_LABEL) as ServiceType[];

export function NewBookingPage() {
  const { isAuthenticated, user } = useAuth();
  const navigate = useNavigate();

  const [customer, setCustomer] = useState<Customer | null>(null);
  const [petId, setPetId] = useState("");
  const [serviceType, setServiceType] = useState<ServiceType>("BANHO");
  const [date, setDate] = useState("");
  const [time, setTime] = useState("10:00");
  const [observations, setObservations] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!user) return;
    customerService.getCustomer(user.id).then((c) => {
      setCustomer(c);
      setPetId(c.pets[0]?.id ?? "");
    });
  }, [user]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isAuthenticated) {
      navigate("/entrar", { state: { from: { pathname: "/agendar" } } });
      return;
    }
    if (!customer || !petId || !date) return;

    setSubmitting(true);
    setError(null);
    try {
      const [year, month, day] = date.split("-").map(Number);
      const [hour, minute] = time.split(":").map(Number);
      const booking = await bookingService.createBooking({
        petId,
        ownerName: customer.name,
        ownerCpf: customer.cpf,
        ownerContact: customer.phone,
        ownerEmail: customer.email,
        serviceType,
        bookingDateTime: formatBookingDateTime(new Date(year, month - 1, day, hour, minute)),
        observations,
      });
      navigate("/minha-conta/agendamentos", { state: { newBookingId: booking.id } });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível criar o agendamento.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-6)", maxWidth: 520 }}>
      <PawBackground tint="var(--primary-tint)">
        <div style={{ padding: "var(--space-6)" }}>
          <h1 className="title" style={{ margin: 0 }}>
            Agendar serviço
          </h1>
          <p className="body" style={{ margin: "var(--space-2) 0 0", color: "var(--ink-muted)" }}>
            Banho, tosa ou consulta veterinária — escolha o pet e o melhor horário.
          </p>
        </div>
      </PawBackground>

      {!isAuthenticated ? (
        <p className="body">
          Você precisa estar logado para agendar. <a href="/entrar">Entrar</a>
        </p>
      ) : !customer ? (
        <p className="body">Carregando…</p>
      ) : customer.pets.length === 0 ? (
        <p className="body">Cadastre um pet antes de agendar um serviço.</p>
      ) : (
        <form onSubmit={handleSubmit} className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
          <SelectField label="Pet" value={petId} onChange={(e) => setPetId(e.target.value)}>
            {customer.pets.map((pet) => (
              <option key={pet.id} value={pet.id}>
                {pet.petName} ({pet.petBreed})
              </option>
            ))}
          </SelectField>

          <SelectField
            label="Serviço"
            value={serviceType}
            onChange={(e) => setServiceType(e.target.value as ServiceType)}
          >
            {SERVICE_TYPES.map((type) => (
              <option key={type} value={type}>
                {SERVICE_TYPE_LABEL[type]}
              </option>
            ))}
          </SelectField>

          <div style={{ display: "flex", gap: "var(--space-3)" }}>
            <TextField label="Data" type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
            <TextField label="Horário" type="time" value={time} onChange={(e) => setTime(e.target.value)} required />
          </div>

          <TextField
            label="Observações"
            value={observations}
            onChange={(e) => setObservations(e.target.value)}
            hint="Alguma informação importante para quem for atender?"
          />

          {error && (
            <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
              {error}
            </p>
          )}

          <Button variant="primary" type="submit" disabled={submitting}>
            {submitting ? "Agendando…" : "Confirmar agendamento"}
          </Button>
        </form>
      )}
    </div>
  );
}
