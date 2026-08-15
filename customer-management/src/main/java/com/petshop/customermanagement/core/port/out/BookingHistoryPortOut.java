package com.petshop.customermanagement.core.port.out;

import com.petshop.customermanagement.core.domain.BookingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingHistoryPortOut {

    Optional<BookingHistory> findByBookingId(UUID bookingId);

    List<BookingHistory> findAllByCustomerId(UUID customerId);

    /** Histórico de agendamentos da loja inteira — visão ADMIN/RECEPTIONIST. */
    Page<BookingHistory> findAll(Pageable pageable);

    BookingHistory save(BookingHistory history);
}
