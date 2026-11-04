package com.petshop.booking_service.core.port.out.dto;

import com.petshop.booking_service.core.domain.AvailableSlot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.format.DateTimeFormatter;

@Data
public class AvailableSlotResponseDto {

    @Schema(description = "Data e hora em ISO-8601 sem offset (horário de Brasília)", example = "2026-08-15T09:00:00")
    private final String dateTime;
    private final int availableStaff;

    public AvailableSlotResponseDto(AvailableSlot slot) {
        this.dateTime = slot.dateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.availableStaff = slot.availableStaff();
    }
}
