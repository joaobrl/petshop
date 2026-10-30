package com.petshop.order_service.core.domain;

/**
 * Resultado do checkout: {@code created=false} quando a chamada repetiu uma
 * Idempotency-Key já usada e devolveu o pedido existente (o controller
 * responde 200 em vez de 201).
 */
public record CheckoutResult(Order order, boolean created) {
}
