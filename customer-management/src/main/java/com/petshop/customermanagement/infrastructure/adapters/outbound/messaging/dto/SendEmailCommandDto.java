package com.petshop.customermanagement.infrastructure.adapters.outbound.messaging.dto;

import java.util.Map;

/**
 * Mesmo contrato (nome dos campos) que os outros produtores de
 * notification-commands (booking-service, order-service) — cada serviço
 * mantém sua própria cópia local, não tem DTO compartilhado. {@code tipo}
 * precisa bater com o nome de uma constante de {@code TipoNotificacao} no
 * notification-service.
 */
public record SendEmailCommandDto(String to, String tipo, Map<String, Object> params) {
}
