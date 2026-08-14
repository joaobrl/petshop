package com.petshop.staff_service.api.rest.dto;

import com.petshop.staff_service.core.domain.AvailabilityResult;
import lombok.Data;

import java.time.format.DateTimeFormatter;

@Data
public class AvailabilityResponseDto {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Integer availableSlots;
    private final Boolean isAvailable;
    private final String validatedDateTime;

    public AvailabilityResponseDto(AvailabilityResult result) {
        this.availableSlots = result.getAvailableSlots();
        this.isAvailable = result.isAvailable();
        this.validatedDateTime = result.getValidatedDateTime().format(FORMATTER);
    }
}
