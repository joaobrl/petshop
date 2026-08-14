package com.petshop.customermanagement.core.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class PurchaseHistory {
    private UUID orderId;
    private UUID customerId;
    private List<PurchaseHistoryItem> items;
    private Double totalAmount;
    private LocalDateTime paidAt;
}
