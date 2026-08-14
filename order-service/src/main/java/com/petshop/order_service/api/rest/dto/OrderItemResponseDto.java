package com.petshop.order_service.api.rest.dto;

import com.petshop.order_service.core.domain.OrderItem;
import lombok.Data;

@Data
public class OrderItemResponseDto {
    private Long productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;
    private Double subtotal;

    public OrderItemResponseDto(OrderItem item) {
        this.productId = item.getProductId();
        this.productName = item.getProductName();
        this.quantity = item.getQuantity();
        this.unitPrice = item.getUnitPrice();
        this.subtotal = item.subtotal();
    }
}
