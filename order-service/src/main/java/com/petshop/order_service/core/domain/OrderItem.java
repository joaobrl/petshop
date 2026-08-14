package com.petshop.order_service.core.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    private Long productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;

    public OrderItem(CartItem cartItem) {
        this.productId = cartItem.getProductId();
        this.productName = cartItem.getProductName();
        this.quantity = cartItem.getQuantity();
        this.unitPrice = cartItem.getUnitPrice();
    }

    public double subtotal() {
        return unitPrice * quantity;
    }
}
