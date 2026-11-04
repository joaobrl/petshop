package com.petshop.booking_service.core.port.in.dto;

import com.petshop.booking_service.core.domain.enums.ServiceType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingUpdateDto {

    private ServiceType serviceType;

    @Schema(description = "Data e hora em ISO-8601 sem offset (horário de Brasília)", example = "2026-08-15T09:00:00")
    private LocalDateTime bookingDateTime;

    private String observations;
}
