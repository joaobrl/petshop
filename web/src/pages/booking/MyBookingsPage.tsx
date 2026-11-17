import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import * as bookingService from "../../services/bookingService";
import * as customerService from "../../services/customerService";
import { BookingStatusBadge } from "../../components/ui/StatusBadges";
import { EmptyState, Loading, PageHeader } from "../../components/ui/Feedback";
import { DateLeaf } from "../../components/ui/DateLeaf";
import { PlusIcon } from "../../components/ui/Icon";
import { SERVICE_TYPE_LABEL } from "../../types/booking";
import type { Booking } from "../../types/booking";
import type { Customer } from "../../types/customer";
import { formatCurrency } from "../../utils/format";
import { dateParts, formatDateTime } from "../../utils/date";

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
    <div className="container page page-narrow">
      <PageHeader eyebrow="na agenda da loja" title="Meus agendamentos">
        <Link to="/agendar" className="btn btn-primary">
          <PlusIcon size={18} />
          Novo agendamento
        </Link>
      </PageHeader>

      {bookings === null && <Loading>Abrindo a agenda…</Loading>}

      {bookings?.length === 0 && (
        <EmptyState title="Nenhum agendamento ainda" note="a banheira está livre!">
          <Link to="/agendar" className="btn btn-primary">
            Marcar um horário
          </Link>
        </EmptyState>
      )}

      {bookings && bookings.length > 0 && (
        <ul className="list">
          {bookings.map((booking) => (
            <li key={booking.id} className="list-row">
              <DateLeaf value={booking.bookingDateTime} />
              <div className="list-row-main">
                <p className="list-row-title">
                  {SERVICE_TYPE_LABEL[booking.serviceType.serviceType]} — {petName(booking.petId)}
                </p>
                <p className="list-row-sub">
                  {dateParts(booking.bookingDateTime)
                    ? `às ${dateParts(booking.bookingDateTime)!.time}`
                    : formatDateTime(booking.bookingDateTime)}
                </p>
              </div>
              <div className="list-row-actions">
                <BookingStatusBadge status={booking.status} />
                <span className="price-tag">{formatCurrency(booking.serviceType.price)}</span>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
