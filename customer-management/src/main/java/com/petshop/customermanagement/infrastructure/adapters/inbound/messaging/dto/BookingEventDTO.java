package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.LocalDateTime;
import java.util.UUID;

public record BookingEventDTO(
        UUID id,
        UUID petId,
        String ownerName,
        String ownerCpf,
        String ownerContact,
        ServiceDetailsEventDTO serviceType,
        // ISO-8601 (formato novo) ou o formato brasileiro legado (eventos antigos ainda na fila).
        @JsonDeserialize(using = TolerantLocalDateTimeDeserializer.class) LocalDateTime bookingDateTime,
        String status,
        String observations
) {
}
