package com.petshop.booking_service.core.domain;

import java.time.LocalDateTime;

/**
 * Um horário que ainda pode ser agendado: já descontando funcionários
 * ocupados naquele slot, sobrou pelo menos 1 livre.
 */
public record AvailableSlot(LocalDateTime dateTime, int availableStaff) {
}
