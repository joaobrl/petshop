package com.petshop.booking_service.core.port.in.dto;

import com.petshop.booking_service.core.domain.enums.ServiceType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingRequestDto {

    @NotNull
    private String petId;

    @NotNull
    private String ownerName;

    @NotNull
    private String ownerCpf;

    @NotNull
    private String ownerContact;

    @NotNull
    private String ownerEmail;

    @NotNull
    private ServiceType serviceType;

    @NotNull
    @FutureOrPresent
    @Schema(description = "Data e hora em ISO-8601 sem offset (horário de Brasília)", example = "2026-08-15T09:00:00")
    private LocalDateTime bookingDateTime;

    private String observations;

}
