import * as bookingService from "../../services/bookingService";
import { useApi } from "../../hooks/useApi";
import { ErrorState } from "../../components/ErrorState";

function StatCard({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="card" style={{ flex: 1, minWidth: 160 }}>
      <span className="label" style={{ color: "var(--ink-muted)" }}>
        {label}
      </span>
      <p className="display" style={{ marginTop: "var(--space-2)" }}>
        {value}
      </p>
    </div>
  );
}

export function InternalDashboardPage() {
  const { data: bookings, error, retry } = useApi(() => bookingService.listBookings(), []);

  const scheduled = bookings?.filter((b) => b.status === "SCHEDULED").length ?? 0;
  const completed = bookings?.filter((b) => b.status === "COMPLETED").length ?? 0;

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-6)" }}>
      <h1 className="title">Painel</h1>
      {error ? (
        <ErrorState message={error} onRetry={retry} />
      ) : (
        <div style={{ display: "flex", gap: "var(--space-4)", flexWrap: "wrap" }}>
          <StatCard label="Agendamentos em aberto" value={bookings ? scheduled : "…"} />
          <StatCard label="Agendamentos concluídos" value={bookings ? completed : "…"} />
        </div>
      )}
      <p className="caption" style={{ color: "var(--ink-muted)" }}>
        Estatísticas de pedidos não aparecem aqui: o order-service não tem um endpoint que liste todos
        os pedidos (ver a página Pedidos).
      </p>
    </div>
  );
}
