package com.petshop.order_service.infrastructure.persistence.postgresql.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemEmbeddable {

    private Long productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;
}
