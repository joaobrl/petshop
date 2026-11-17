import { useEffect, useState } from "react";
import type { CSSProperties } from "react";
import * as staffService from "../../services/staffService";
import { ApiError } from "../../services/http";
import { ROLE_LABEL } from "../../types/staff";
import type { Staff, WeekendAllocation } from "../../types/staff";
import { InitialsAvatar } from "../../components/ui/InitialsAvatar";
import { TextField } from "../../components/ui/Field";
import { Button } from "../../components/ui/Button";
import { Loading, Notice, PageHeader } from "../../components/ui/Feedback";
import { formatDate } from "../../utils/date";

export function StaffPage() {
  const [staff, setStaff] = useState<Staff[] | null>(null);
  const [staffError, setStaffError] = useState<string | null>(null);

  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [allocations, setAllocations] = useState<WeekendAllocation[] | null>(null);
  const [allocationError, setAllocationError] = useState<string | null>(null);
  const [loadingAllocation, setLoadingAllocation] = useState(false);

  useEffect(() => {
    staffService
      .listStaff()
      .then(setStaff)
      .catch((err) => {
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
      setAllocations(await staffService.getWeekendAllocation(date));
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
    <div className="stack" style={{ "--gap": "var(--space-10)" } as CSSProperties}>
      <section>
        <PageHeader eyebrow="quem cuida da casa" title="Equipe" />
        {staffError && <Notice tone="danger">{staffError}</Notice>}
        {staff === null && !staffError && <Loading>Chamando a equipe…</Loading>}
        {staff && (
          <ul className="list">
            {staff.map((person) => (
              <li key={person.id} className="list-row">
                <InitialsAvatar name={person.name} size={48} />
                <div className="list-row-main">
                  <p className="list-row-title">{person.name}</p>
                  <p className="list-row-sub">
                    {ROLE_LABEL[person.role]} · {person.email}
                  </p>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="stack">
        <h2 className="heading">Escala de sábado/domingo</h2>
        <form onSubmit={handleCheckAllocation} className="row row-wrap" style={{ alignItems: "flex-end" }}>
          <div style={{ flex: "0 1 220px" }}>
            <TextField label="Data" type="date" value={date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <Button variant="outline" type="submit" disabled={loadingAllocation}>
            {loadingAllocation ? "Consultando…" : "Consultar"}
          </Button>
        </form>

        {allocationError && <Notice tone="danger">{allocationError}</Notice>}
        {allocations?.length === 0 && <Notice tone="muted">Nenhuma escala para essa data.</Notice>}
        {allocations && allocations.length > 0 && (
          <ul className="list">
            {allocations.map((allocation) => (
              <li key={allocation.staffId + allocation.allocationDate} className="list-row">
                <InitialsAvatar name={allocation.staffName} size={40} />
                <div className="list-row-main">
                  <p className="list-row-title">{allocation.staffName}</p>
                </div>
                <span className="mono body-strong">{formatDate(`${allocation.allocationDate}T00:00:00`)}</span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
