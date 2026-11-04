import { Link } from "react-router-dom";
import { useApi } from "../../hooks/useApi";
import { ErrorState } from "../../components/ErrorState";
import { useAuth } from "../../context/AuthContext";
import * as bookingService from "../../services/bookingService";
import * as customerService from "../../services/customerService";
import { BookingStatusBadge } from "../../components/ui/StatusBadges";
import { SERVICE_TYPE_LABEL } from "../../types/booking";
import { formatCurrency } from "../../utils/format";
import { formatDateTime } from "../../utils/date";


export function MyBookingsPage() {
  const { user } = useAuth();
  const { data: bookings, error: bookingsError, retry: retryBookings } = useApi(
    () => bookingService.listBookings({ ownerCpf: user!.cpf }),
    [user],
    !!user,
  );
  const { data: customer, error: customerError, retry: retryCustomer } = useApi(
    () => customerService.getCustomer(user!.id),
    [user],
    !!user,
  );
  const error = bookingsError ?? customerError;

  function retry() {
    retryBookings();
    retryCustomer();
  }

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

      {error && <ErrorState message={error} onRetry={retry} />}
      {!bookings && !error && <p className="body">Carregando…</p>}
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
            {formatDateTime(booking.bookingDateTime)}
          </span>
          <span className="body-strong">{formatCurrency(booking.serviceType.price)}</span>
        </div>
      ))}
    </div>
  );
}
