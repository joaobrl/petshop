package com.petshop.booking_service.core.port.in;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.core.port.in.dto.BookingUpdateDto;
import com.petshop.booking_service.core.port.in.dto.PaymentConfirmationRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface BookingPortIn {
    Booking createBooking(BookingRequestDto request);

    Page<Booking> findBookings(BookingSearchCriteriaDto criteria, Pageable pageable);

    Booking findBookingById(UUID id);

    Booking updateBooking(UUID id, BookingUpdateDto bookingUpdate, String status);

    Booking cancelBooking(UUID id);

    Booking confirmPayment(UUID id, PaymentConfirmationRequestDto request);
}
