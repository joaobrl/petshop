package com.petshop.order_service.api.rest.dto;

import com.petshop.order_service.core.domain.CartItem;
import lombok.Data;

@Data
public class CartItemResponseDto {
    private Long productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;
    private Double subtotal;
    private boolean reserved;

    public CartItemResponseDto(CartItem item) {
        this.productId = item.getProductId();
        this.productName = item.getProductName();
        this.quantity = item.getQuantity();
        this.unitPrice = item.getUnitPrice();
        this.subtotal = item.subtotal();
        this.reserved = item.isReserved();
    }
}
