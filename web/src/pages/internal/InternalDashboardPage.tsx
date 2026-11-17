import { useEffect, useState } from "react";
import type { CSSProperties } from "react";
import { Link } from "react-router-dom";
import * as bookingService from "../../services/bookingService";
import type { Booking } from "../../types/booking";
import { SERVICE_TYPE_LABEL } from "../../types/booking";
import { useAuth } from "../../context/AuthContext";
import { Notice, PageHeader } from "../../components/ui/Feedback";
import { DateLeaf } from "../../components/ui/DateLeaf";
import { dateParts } from "../../utils/date";

function StatCard({ label, value, tile }: { label: string; value: number | string; tile: string }) {
  return (
    <div className="stat" style={{ "--tile": tile } as CSSProperties}>
      <span className="label">{label}</span>
      <p className="stat-value">{value}</p>
    </div>
  );
}

export function InternalDashboardPage() {
  const { user } = useAuth();
  const [bookings, setBookings] = useState<Booking[] | null>(null);

  useEffect(() => {
    bookingService.listBookings().then(setBookings);
  }, []);

  const open = bookings?.filter((b) => b.status === "SCHEDULED") ?? [];
  const completed = bookings?.filter((b) => b.status === "COMPLETED").length ?? 0;
  const next = [...open].sort((a, b) => a.bookingDateTime.localeCompare(b.bookingDateTime)).slice(0, 4);
  const firstName = user?.name?.split(" ")[0];

  return (
    <div className="stack" style={{ "--gap": "var(--space-8)" } as CSSProperties}>
      <PageHeader eyebrow={firstName ? `oi, ${firstName}!` : "bom trabalho!"} title="Painel" />

      <div className="stat-grid">
        <StatCard label="Agendamentos em aberto" value={bookings ? open.length : "–"} tile="var(--primary-tint)" />
        <StatCard label="Agendamentos concluídos" value={bookings ? completed : "–"} tile="var(--grass-tint)" />
      </div>

      {next.length > 0 && (
        <section className="stack">
          <div className="row spread row-wrap">
            <h2 className="heading">Próximos na agenda</h2>
            <Link to="/interno/agendamentos" className="link">
              Ver todos
            </Link>
          </div>
          <ul className="list">
            {next.map((booking) => (
              <li key={booking.id} className="list-row">
                <DateLeaf value={booking.bookingDateTime} />
                <div className="list-row-main">
                  <p className="list-row-title">
                    {SERVICE_TYPE_LABEL[booking.serviceType.serviceType]} — {booking.ownerName}
                  </p>
                  <p className="list-row-sub">às {dateParts(booking.bookingDateTime)?.time ?? "—"}</p>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      <Notice tone="muted">
        Estatísticas de pedidos não aparecem aqui: o order-service não tem um endpoint que liste todos os pedidos
        (ver a página Pedidos).
      </Notice>
    </div>
  );
}
