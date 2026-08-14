package com.petshop.order_service.core.domain.enums;

public enum OrderStatus {
    AWAITING_PAYMENT,
    PAID,
    /** Pagamento confirmado, prazo de preparo (ver Order#pickupReadyAt) já venceu — pode retirar na loja. */
    READY_FOR_PICKUP,
    CANCELED
}
