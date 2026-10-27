import { useEffect, useState } from "react";
import * as bookingService from "../../services/bookingService";
import { ApiError } from "../../services/http";
import { BookingStatusBadge } from "../../components/ui/StatusBadges";
import { Button } from "../../components/ui/Button";
import { SERVICE_TYPE_LABEL } from "../../types/booking";
import type { Booking } from "../../types/booking";

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
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-4)" }}>
      <h1 className="title">Agendamentos</h1>
      {error && (
        <p className="body" style={{ color: "var(--danger)", margin: 0 }}>
          {error}
        </p>
      )}
      {sorted === null && <p className="body">Carregando…</p>}
      {sorted?.length === 0 && <p className="body">Nenhum agendamento.</p>}
      <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
        {sorted?.map((booking) => (
          <div key={booking.id} className="card" style={{ display: "flex", alignItems: "center", gap: "var(--space-4)" }}>
            <div style={{ flex: 1 }}>
              <p className="body-strong" style={{ margin: 0 }}>
                {SERVICE_TYPE_LABEL[booking.serviceType.serviceType]} — {booking.ownerName}
              </p>
              <p className="caption" style={{ margin: "4px 0 0", color: "var(--ink-muted)" }}>
                {booking.bookingDateTime} · CPF {booking.ownerCpf}
              </p>
            </div>
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
        ))}
      </div>
    </div>
  );
}
