package com.petshop.customermanagement.core.port.in.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Entrada de {@code UpdateCustomerPurchaseHistoryUseCase} — tradução do
 * evento Kafka "order-completed" (OrderCompletedEventDTO) feita em
 * OrderHistoryKafkaListener (infra), pelo mesmo motivo do
 * BookingCompletedCommand: o core não deve depender de um DTO de
 * mensageria.
 */
public record PurchaseCompletedCommand(
        UUID orderId,
        UUID customerId,
        List<PurchaseItemCommand> items,
        Double totalAmount,
        LocalDateTime paidAt
) {
    public record PurchaseItemCommand(
            Long productId,
            String productName,
            Integer quantity,
            Double unitPrice
    ) {
    }
}
