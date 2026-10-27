import { useEffect, useState } from "react";
import * as staffService from "../../services/staffService";
import { ApiError } from "../../services/http";
import { ROLE_LABEL } from "../../types/staff";
import type { Staff, WeekendAllocation } from "../../types/staff";
import { InitialsAvatar } from "../../components/ui/InitialsAvatar";
import { TextField } from "../../components/ui/Field";
import { Button } from "../../components/ui/Button";

function toBackendDate(isoDate: string) {
  const [year, month, day] = isoDate.split("-");
  return `${day}/${month}/${year}`;
}

export function StaffPage() {
  const [staff, setStaff] = useState<Staff[] | null>(null);
  const [staffError, setStaffError] = useState<string | null>(null);

  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [allocations, setAllocations] = useState<WeekendAllocation[] | null>(null);
  const [allocationError, setAllocationError] = useState<string | null>(null);
  const [loadingAllocation, setLoadingAllocation] = useState(false);

  useEffect(() => {
    staffService.listStaff().then(setStaff).catch((err) => {
      // customer-management restricts GET /staff/all to ADMIN (or the
      // internal SERVICE token) — any other role gets a 401/403 here, with
      // no ProblemDetail body to read a message from.
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        setStaffError("Só uma conta de administrador pode ver a lista de equipe.");
        return;
      }
      setStaffError(err instanceof ApiError ? err.message : "Não foi possível carregar a equipe.");
    });
  }, []);

  async function handleCheckAllocation(e: React.FormEvent) {
    e.preventDefault();
    setLoadingAllocation(true);
    setAllocationError(null);
    try {
      setAllocations(await staffService.getWeekendAllocation(toBackendDate(date)));
    } catch (err) {
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        setAllocationError("Só uma conta de administrador pode consultar a escala.");
        return;
      }
      setAllocationError(err instanceof ApiError ? err.message : "Não foi possível consultar a escala.");
    } finally {
      setLoadingAllocation(false);
    }
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-8)" }}>
      <div>
        <h1 className="title" style={{ marginBottom: "var(--space-4)" }}>
          Equipe
        </h1>
        {staffError && (
          <p className="body" style={{ color: "var(--danger)" }}>
            {staffError}
          </p>
        )}
        {staff === null && !staffError && <p className="body">Carregando…</p>}
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-3)" }}>
          {staff?.map((person) => (
            <div key={person.id} className="card" style={{ display: "flex", alignItems: "center", gap: "var(--space-4)" }}>
              <InitialsAvatar name={person.name} size={40} />
              <div>
                <p className="body-strong" style={{ margin: 0 }}>
                  {person.name}
                </p>
                <p className="caption" style={{ margin: "4px 0 0", color: "var(--ink-muted)" }}>
                  {ROLE_LABEL[person.role]} · {person.email}
                </p>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div>
        <h2 className="heading" style={{ marginBottom: "var(--space-4)" }}>
          Escala de sábado/domingo
        </h2>
        <form onSubmit={handleCheckAllocation} style={{ display: "flex", gap: "var(--space-3)", alignItems: "flex-end", marginBottom: "var(--space-4)" }}>
          <div style={{ flex: 1, maxWidth: 220 }}>
            <TextField label="Data" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <Button variant="outline" type="submit" disabled={loadingAllocation}>
            {loadingAllocation ? "Consultando…" : "Consultar"}
          </Button>
        </form>

        {allocationError && (
          <p className="body" style={{ color: "var(--danger)" }}>
            {allocationError}
          </p>
        )}
        {allocations?.length === 0 && <p className="body">Nenhuma escala para essa data.</p>}
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-2)" }}>
          {allocations?.map((allocation) => (
            <div
              key={allocation.staffId + allocation.allocationDate}
              className="card"
              style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}
            >
              <span className="body">{allocation.allocationDate}</span>
              <span className="body-strong">{allocation.staffName}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
