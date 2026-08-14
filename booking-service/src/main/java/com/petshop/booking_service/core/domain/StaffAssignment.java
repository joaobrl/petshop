package com.petshop.booking_service.core.domain;

import java.util.UUID;

/**
 * Retrato mínimo de um funcionário escalado, obtido via Feign do
 * staff-service — só o que o booking-service precisa pra atribuir o
 * atendimento.
 */
public record StaffAssignment(UUID id, String name) {
}
