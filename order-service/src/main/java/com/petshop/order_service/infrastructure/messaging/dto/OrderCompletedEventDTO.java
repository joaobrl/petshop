package com.petshop.order_service.infrastructure.messaging.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Evento publicado quando um pedido é pago. Consumido pelo customer-management
 * pra gravar o histórico de compras no Mongo (mesmo padrão do histórico de
 * agendamento, que já existe).
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
