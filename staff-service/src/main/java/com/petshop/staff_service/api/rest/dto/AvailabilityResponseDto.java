package com.petshop.staff_service.api.rest.dto;

import com.petshop.staff_service.core.domain.AvailabilityResult;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.format.DateTimeFormatter;

@Data
public class AvailabilityResponseDto {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Integer availableSlots;
    private final Boolean isAvailable;
    @Schema(description = "Data e hora validada em ISO-8601 sem offset (horário de Brasília)", example = "2026-08-15T09:00:00")
    private final String validatedDateTime;

    public AvailabilityResponseDto(AvailabilityResult result) {
        this.availableSlots = result.getAvailableSlots();
        this.isAvailable = result.isAvailable();
        this.validatedDateTime = result.getValidatedDateTime().format(FORMATTER);
    }
}
