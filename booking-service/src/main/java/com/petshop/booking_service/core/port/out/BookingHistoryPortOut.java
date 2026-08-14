package com.petshop.booking_service.core.port.out;

import com.petshop.booking_service.core.port.out.dto.BookingResponseDto;

public interface BookingHistoryPortOut {

    void publishBookingCompleted(BookingResponseDto bookingEvent);

    void publishBookingScheduled(BookingResponseDto bookingEvent);

    void publishBookingCanceled(BookingResponseDto bookingEvent);
}
