package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookingEventDTO(
        UUID id,
        UUID petId,
        String ownerName,
        String ownerCpf,
        String ownerContact,
        ServiceDetailsEventDTO serviceType,
        @JsonFormat(pattern = "dd/MM/yyyy HH:mm") LocalDateTime bookingDateTime,
        String status,
        String observations
) {
}
