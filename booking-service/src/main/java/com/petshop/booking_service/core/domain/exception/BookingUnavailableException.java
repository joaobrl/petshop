package com.petshop.booking_service.core.domain.exception;

import com.petshop.commons.exception.BusinessRuleException;

import java.time.LocalDateTime;

public class BookingUnavailableException extends BusinessRuleException {

    private final LocalDateTime suggestedSlot;

    public BookingUnavailableException(String message, LocalDateTime suggestedSlot) {
        super(message);
        this.suggestedSlot = suggestedSlot;
    }

    public LocalDateTime getSuggestedSlot() {
        return suggestedSlot;
    }
}
