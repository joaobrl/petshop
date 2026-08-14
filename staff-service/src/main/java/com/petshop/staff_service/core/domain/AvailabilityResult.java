package com.petshop.staff_service.core.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AvailabilityResult {

    private final int availableSlots;
    private final boolean available;
    private final LocalDateTime validatedDateTime;
}
