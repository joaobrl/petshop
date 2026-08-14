package com.petshop.booking_service.core.port.out;

import com.petshop.booking_service.core.domain.Booking;

public interface NotificationPortOut {
    void sendBookingScheduled(Booking booking);
    void sendBookingCompleted(Booking booking);
    void sendBookingCanceled(Booking booking);
}
