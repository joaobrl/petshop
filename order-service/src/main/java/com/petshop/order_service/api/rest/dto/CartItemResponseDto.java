package com.petshop.order_service.api.rest.dto;

import com.petshop.order_service.core.domain.CartItem;
import com.petshop.order_service.core.domain.enums.StockAvailability;
import lombok.Data;

@Data
public class CartItemResponseDto {
    private Long productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;
    private Double subtotal;
    /**
     * Disponibilidade atual (não persistida); null quando não deu pra
     * consultar o product-registration nesta leitura.
     */
    private StockAvailability availability;

    public CartItemResponseDto(CartItem item, StockAvailability availability) {
        this.productId = item.getProductId();
        this.productName = item.getProductName();
        this.quantity = item.getQuantity();
        this.unitPrice = item.getUnitPrice();
        this.subtotal = item.subtotal();
        this.availability = availability;
    }
}
