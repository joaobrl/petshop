package com.petshop.customermanagement.infrastructure.persistence.mongo.entity;

import com.petshop.customermanagement.core.domain.PurchaseHistoryItem;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Document(collection = "customer_purchase_history")
public class CustomerPurchaseHistory {

    @Id
    private String id;

    private UUID customerId;
    private UUID orderId;
    private List<PurchaseHistoryItem> items;
    private Double totalAmount;
    private LocalDateTime paidAt;
}
