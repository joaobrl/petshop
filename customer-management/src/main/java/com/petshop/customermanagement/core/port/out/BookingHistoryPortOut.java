package com.petshop.customermanagement.core.port.out;

import com.petshop.customermanagement.core.domain.BookingHistory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingHistoryPortOut {

    Optional<BookingHistory> findByBookingId(UUID bookingId);

    List<BookingHistory> findAllByCustomerId(UUID customerId);

    /** Histórico de agendamentos da loja inteira — visão ADMIN/RECEPTIONIST. */
    List<BookingHistory> findAll();

    BookingHistory save(BookingHistory history);
}
