import { useEffect, useState } from "react";
import type { CSSProperties } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import * as customerService from "../../services/customerService";
import * as bookingService from "../../services/bookingService";
import { ApiError } from "../../services/http";
import type { Customer } from "../../types/customer";
import type { ServiceType } from "../../types/booking";
import { SERVICE_TYPE_LABEL, formatBookingDateTime } from "../../types/booking";
import { Button } from "../../components/ui/Button";
import { TextField } from "../../components/ui/Field";
import { Loading, Notice } from "../../components/ui/Feedback";
import { PetAvatar } from "../../components/ui/PetAvatar";
import { Polaroid } from "../../components/ui/Polaroid";
import { ServiceIcon } from "../../components/ui/Icon";
import { AVATAR_OPTIONS } from "../../mocks/avatarOptions";
import { SERVICE_BLURB, SERVICE_TILE, isServiceType } from "../../utils/serviceInfo";

const SERVICE_TYPES = Object.keys(SERVICE_TYPE_LABEL) as ServiceType[];

const BUBBLES = [
  { left: "8%", top: "62%", size: 26, delay: "0s" },
  { left: "78%", top: "12%", size: 40, delay: "1.2s" },
  { left: "88%", top: "48%", size: 18, delay: "2.4s" },
  { left: "66%", top: "78%", size: 30, delay: "0.6s" },
];

function todayIso(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

export function NewBookingPage() {
  const { isAuthenticated, user } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const requestedService = searchParams.get("servico");

  const [customer, setCustomer] = useState<Customer | null>(null);
  const [petId, setPetId] = useState("");
  const [serviceType, setServiceType] = useState<ServiceType>(
    isServiceType(requestedService) ? requestedService : "BANHO",
  );
  const [date, setDate] = useState("");
  const [time, setTime] = useState("10:00");
  // Free times for the chosen day. null = unknown (not asked yet, or the
  // lookup failed), in which case the customer just types a time.
  const [slotLookup, setSlotLookup] = useState<{ key: string; times: string[] } | null>(null);
  const slotKey = serviceType + "|" + date;
  const slots = date && slotLookup?.key === slotKey ? slotLookup.times : null;
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

  useEffect(() => {
    if (!date) return;
    const key = serviceType + "|" + date;
    let cancelled = false;
    bookingService
      .getAvailableSlots(serviceType, date, "DAY")
      .then((list) => {
        if (cancelled) return;
        const times = [
          ...new Set(
            list
              .filter((slot) => slot.availableStaff > 0 && slot.dateTime.startsWith(date))
              .map((slot) => slot.dateTime.slice(11, 16)),
          ),
        ].sort();
        setSlotLookup({ key, times });
        if (times.length > 0) setTime((current) => (times.includes(current) ? current : times[0]));
      })
      .catch(() => {
        if (!cancelled) setSlotLookup(null);
      });
    return () => {
      cancelled = true;
    };
  }, [date, serviceType]);

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
    <div className="container page">
      <div className="booking">
        <section className="booking-intro">
          <div className="bubbles" aria-hidden="true">
            {BUBBLES.map((bubble) => (
              <span
                key={bubble.left}
                style={{
                  left: bubble.left,
                  top: bubble.top,
                  width: bubble.size,
                  height: bubble.size,
                  animationDelay: bubble.delay,
                }}
              />
            ))}
          </div>
          <span className="eyebrow">com hora marcada</span>
          <h1 className="title">Agendar serviço</h1>
          <p className="lead">Banho, tosa ou consulta veterinária — escolha o pet e o melhor horário.</p>
          <Polaroid src="/images/avatars/dachshund.webp" caption="cheiroso e de laço novo" tilt={-4} tile="#fff" />
        </section>

        {!isAuthenticated ? (
          <div className="card stack">
            <h2 className="heading">Entre para marcar um horário</h2>
            <p className="muted">
              Precisamos saber quem é o tutor e qual pet vem nos visitar. É rapidinho.
            </p>
            <div className="row row-wrap">
              <Link to="/entrar" state={{ from: { pathname: "/agendar" } }} className="btn btn-primary">
                Entrar
              </Link>
              <Link to="/cadastro" className="btn btn-outline">
                Criar conta
              </Link>
            </div>
          </div>
        ) : !customer ? (
          <Loading>Preparando a agenda…</Loading>
        ) : customer.pets.length === 0 ? (
          <div className="card stack">
            <h2 className="heading">Primeiro, quem vem?</h2>
            <p className="muted">Cadastre um pet antes de agendar um serviço.</p>
            <div>
              <Link to="/minha-conta/pets" className="btn btn-primary">
                Cadastrar meu pet
              </Link>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="booking-form">
            <fieldset className="fieldset">
              <legend>
                <span className="step-num">1</span>Quem vem?
              </legend>
              <div className="choices" style={{ "--choice-min": "200px" } as CSSProperties}>
                {customer.pets.map((pet) => (
                  <label key={pet.id} className="choice">
                    <input
                      type="radio"
                      name="pet"
                      value={pet.id}
                      checked={petId === pet.id}
                      onChange={() => setPetId(pet.id)}
                    />
                    <PetAvatar
                      name=""
                      petType={pet.petType}
                      avatarUrl={AVATAR_OPTIONS.find((a) => a.label === pet.petBreed)?.url}
                      size={44}
                    />
                    <span>
                      <span className="choice-title">{pet.petName}</span>
                      <br />
                      <span className="choice-sub">{pet.petBreed}</span>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>

            <fieldset className="fieldset">
              <legend>
                <span className="step-num">2</span>O que vamos fazer?
              </legend>
              <div className="choices" style={{ "--choice-min": "230px" } as CSSProperties}>
                {SERVICE_TYPES.map((type) => (
                  <label key={type} className="choice">
                    <input
                      type="radio"
                      name="service"
                      value={type}
                      checked={serviceType === type}
                      onChange={() => setServiceType(type)}
                    />
                    <span className="choice-icon" style={{ "--tile": SERVICE_TILE[type] } as CSSProperties}>
                      <ServiceIcon type={type} size={24} />
                    </span>
                    <span>
                      <span className="choice-title">{SERVICE_TYPE_LABEL[type]}</span>
                      <br />
                      <span className="choice-sub">{SERVICE_BLURB[type]}</span>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>

            <fieldset className="fieldset">
              <legend>
                <span className="step-num">3</span>Quando?
              </legend>
              <div className="stack">
                <div className="field-row" style={{ maxWidth: 460 }}>
                  <TextField
                    label="Data"
                    type="date"
                    min={todayIso()}
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    required
                  />
                  {(slots === null || slots.length === 0) && (
                    <TextField
                      label="Horário"
                      type="time"
                      value={time}
                      onChange={(e) => setTime(e.target.value)}
                      required
                    />
                  )}
                </div>

                {slots !== null && slots.length === 0 && (
                  <Notice tone="warning">
                    Não encontramos horários livres para esse serviço nesse dia. Tente outra data — ou informe o
                    horário desejado e a gente confere.
                  </Notice>
                )}

                {slots !== null && slots.length > 0 && (
                  <div>
                    <p className="field-label" id="horarios-label" style={{ marginBottom: 6 }}>
                      Horários livres
                    </p>
                    <div className="chip-choices" role="radiogroup" aria-labelledby="horarios-label">
                      {slots.map((slot) => (
                        <label key={slot} className="choice">
                          <input
                            type="radio"
                            name="time"
                            value={slot}
                            checked={time === slot}
                            onChange={() => setTime(slot)}
                          />
                          {slot}
                        </label>
                      ))}
                    </div>
                  </div>
                )}

                <TextField
                  label="Observações"
                  value={observations}
                  onChange={(e) => setObservations(e.target.value)}
                  hint="Alguma informação importante para quem for atender?"
                />
              </div>
            </fieldset>

            {error && <Notice tone="danger">{error}</Notice>}

            <div>
              <Button variant="primary" type="submit" size="lg" disabled={submitting}>
                {submitting ? "Agendando…" : "Confirmar agendamento"}
              </Button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
