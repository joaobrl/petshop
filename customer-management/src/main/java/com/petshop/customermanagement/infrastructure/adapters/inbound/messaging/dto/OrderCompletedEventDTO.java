package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Cópia local do formato publicado pelo order-service no tópico
 * "order-completed" (com.petshop.order_service.infrastructure.messaging.dto.OrderCompletedEventDTO).
 * Cada serviço tem sua própria visão do contrato do evento — não compartilhamos
 * a classe entre os módulos.
 */
public record OrderCompletedEventDTO(
        UUID orderId,
        UUID customerId,
        List<OrderItemEventDTO> items,
        Double totalAmount,
        LocalDateTime paidAt
) {
    public record OrderItemEventDTO(
            Long productId,
            String productName,
            Integer quantity,
            Double unitPrice
    ) {}
}
