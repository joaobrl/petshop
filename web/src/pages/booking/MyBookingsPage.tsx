import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import * as bookingService from "../../services/bookingService";
import * as customerService from "../../services/customerService";
import { BookingStatusBadge } from "../../components/ui/StatusBadges";
import { SERVICE_TYPE_LABEL } from "../../types/booking";
import type { Booking } from "../../types/booking";
import type { Customer } from "../../types/customer";

const currency = (value: number) => value.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });

export function MyBookingsPage() {
  const { user } = useAuth();
  const [bookings, setBookings] = useState<Booking[] | null>(null);
  const [customer, setCustomer] = useState<Customer | null>(null);

  useEffect(() => {
    if (!user) return;
    customerService.getCustomer(user.id).then(setCustomer);
    bookingService.listBookings({ ownerCpf: user.cpf }).then(setBookings);
  }, [user]);

  function petName(petId: string) {
    return customer?.pets.find((p) => p.id === petId)?.petName ?? petId.slice(0, 8);
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)", maxWidth: 640 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <h1 className="title">Meus agendamentos</h1>
        <Link to="/agendar" className="btn btn-primary btn-sm" style={{ textDecoration: "none" }}>
          Novo agendamento
        </Link>
      </div>

      {bookings === null && <p className="body">Carregando…</p>}
      {bookings?.length === 0 && <p className="body">Nenhum agendamento ainda.</p>}

      {bookings?.map((booking) => (
        <div key={booking.id} className="card" style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <span className="body-strong">
              {SERVICE_TYPE_LABEL[booking.serviceType.serviceType]} — {petName(booking.petId)}
            </span>
            <BookingStatusBadge status={booking.status} />
          </div>
          <span className="caption" style={{ color: "var(--ink-muted)" }}>
            {booking.bookingDateTime}
          </span>
          <span className="body-strong">{currency(booking.serviceType.price)}</span>
        </div>
      ))}
    </div>
  );
}
