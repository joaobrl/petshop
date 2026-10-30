package com.petshop.booking_service.core.domain;

import java.time.LocalDateTime;


public record AvailableSlot(LocalDateTime dateTime, int availableStaff) {
}
