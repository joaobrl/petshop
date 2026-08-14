package com.petshop.customermanagement.api.rest.dto;

import com.petshop.customermanagement.core.domain.PurchaseHistory;
import com.petshop.customermanagement.core.domain.PurchaseHistoryItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseHistoryResponseDto(
        UUID orderId,
        UUID customerId,
        List<PurchaseHistoryItem> items,
        Double totalAmount,
        LocalDateTime paidAt
) {
    public PurchaseHistoryResponseDto(PurchaseHistory history) {
        this(
                history.getOrderId(),
                history.getCustomerId(),
                history.getItems(),
                history.getTotalAmount(),
                history.getPaidAt()
        );
    }
}
