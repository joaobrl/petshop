package com.petshop.booking_service.core.port.in.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.petshop.booking_service.core.domain.enums.ServiceType;
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
    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime bookingDateTime;

    private String observations;

}
