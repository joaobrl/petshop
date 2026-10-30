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

    Booking createBooking(BookingRequestDto request, Role requesterRole, String token);

    Page<Booking> findBookings(BookingSearchCriteriaDto criteria, Pageable pageable);

    Booking findBookingById(UUID id);

    Booking updateBooking(UUID id, BookingUpdateDto bookingUpdate, String status, Role requesterRole, String token);

    Booking cancelBooking(UUID id);

    Booking confirmPayment(UUID id, PaymentConfirmationRequestDto request);
}
