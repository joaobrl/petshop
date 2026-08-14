package com.petshop.customermanagement.api.rest.dto;

import com.petshop.customermanagement.core.domain.BookingHistory;

import java.util.UUID;

public record BookingHistoryResponseDto(
        UUID bookingId,
        UUID customerId,
        String serviceType,
        String bookingDate,
        String bookingTime,
        String status
) {
    public BookingHistoryResponseDto(BookingHistory history) {
        this(
                history.getBookingId(),
                history.getCustomerId(),
                history.getServiceType(),
                history.getBookingDate(),
                history.getBookingTime(),
                history.getStatus()
        );
    }
}
