import { useEffect, useState } from "react";
import * as bookingService from "../../services/bookingService";
import { ApiError } from "../../services/http";
import { BookingStatusBadge } from "../../components/ui/StatusBadges";
import { Button } from "../../components/ui/Button";
import { EmptyState, Loading, Notice, PageHeader } from "../../components/ui/Feedback";
import { DateLeaf } from "../../components/ui/DateLeaf";
import { SERVICE_TYPE_LABEL } from "../../types/booking";
import type { Booking } from "../../types/booking";
import { formatDateTime } from "../../utils/date";

export function InternalBookingsPage() {
  const [bookings, setBookings] = useState<Booking[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  function reload() {
    bookingService.listBookings().then(setBookings);
  }

  useEffect(reload, []);

  async function handleComplete(id: string) {
    setError(null);
    try {
      await bookingService.updateBookingStatus(id, "COMPLETED");
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível concluir o agendamento.");
    }
  }

  async function handleCancel(id: string) {
    setError(null);
    try {
      await bookingService.cancelBooking(id);
      reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Não foi possível cancelar o agendamento.");
    }
  }

  const sorted = bookings ? [...bookings].sort((a, b) => a.bookingDateTime.localeCompare(b.bookingDateTime)) : null;

  return (
    <div className="stack">
      <PageHeader eyebrow="a agenda da casa" title="Agendamentos" />
      {error && <Notice tone="danger">{error}</Notice>}
      {sorted === null && <Loading>Abrindo a agenda…</Loading>}
      {sorted?.length === 0 && <EmptyState title="Nenhum agendamento" note="dia tranquilo por aqui" />}
      {sorted && sorted.length > 0 && (
        <ul className="list">
          {sorted.map((booking) => (
            <li key={booking.id} className="list-row">
              <DateLeaf value={booking.bookingDateTime} />
              <div className="list-row-main">
                <p className="list-row-title">
                  {SERVICE_TYPE_LABEL[booking.serviceType.serviceType]} — {booking.ownerName}
                </p>
                <p className="list-row-sub">
                  {formatDateTime(booking.bookingDateTime)} · CPF {booking.ownerCpf}
                </p>
              </div>
              <div className="list-row-actions">
                <BookingStatusBadge status={booking.status} />
                {booking.status === "SCHEDULED" && (
                  <>
                    <Button variant="outline" size="sm" onClick={() => handleComplete(booking.id)}>
                      Concluir
                    </Button>
                    <Button variant="danger" size="sm" onClick={() => handleCancel(booking.id)}>
                      Cancelar
                    </Button>
                  </>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
