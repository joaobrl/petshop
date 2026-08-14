package com.petshop.order_service.core.domain;

import java.util.UUID;

/**
 * Retrato mínimo do cliente, obtido via Feign do customer-management, só com
 * o que o order-service precisa (mandar e-mail).
 */
public record CustomerInfo(UUID id, String name, String email) {
}
