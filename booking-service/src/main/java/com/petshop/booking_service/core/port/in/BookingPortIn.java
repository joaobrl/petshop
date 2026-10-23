package com.petshop.booking_service.core.port.in;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.core.port.in.dto.BookingUpdateDto;
import com.petshop.booking_service.core.port.in.dto.PaymentConfirmationRequestDto;
import com.petshop.commons.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BookingPortIn {

    /**
     * {@code requesterRole} ADMIN/RECEPTIONIST dispensa só a antecedência mínima de 2h e a duplicidade de
     * agendamento do mesmo pet no mesmo dia (desde que não haja atendimento pendente/em andamento pra esse
     * pet) — ver BookingService#isBookingRestrictionExempt. Horário de funcionamento/dia da semana e
     * disponibilidade de funcionário continuam valendo pra todo mundo.
     */
    Booking createBooking(BookingRequestDto request, Role requesterRole);

    Page<Booking> findBookings(BookingSearchCriteriaDto criteria, Pageable pageable);

    Booking findBookingById(UUID id);

    Booking updateBooking(UUID id, BookingUpdateDto bookingUpdate, String status, Role requesterRole);

    Booking cancelBooking(UUID id);

    Booking confirmPayment(UUID id, PaymentConfirmationRequestDto request);
}
