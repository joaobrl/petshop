package com.petshop.customermanagement.core.port.in.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entrada de {@code UpdateBookingHistoryUseCase} — só os campos do evento
 * Kafka "booking-completed" que a regra de negócio realmente usa. A
 * tradução do DTO de mensageria (BookingEventDTO) para este command
 * acontece em BookingHistoryKafkaListener (infra), não aqui, pra o core
 * não depender de um tipo de infraestrutura.
 */
public record BookingCompletedCommand(
        UUID bookingId,
        String ownerCpf,
        String serviceType,
        LocalDateTime bookingDateTime,
        String status
) {
}
