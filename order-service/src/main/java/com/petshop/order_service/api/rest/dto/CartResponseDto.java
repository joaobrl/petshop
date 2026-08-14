package com.petshop.order_service.api.rest.dto;

import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.enums.CartStatus;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class CartResponseDto {
    private UUID id;
    private UUID customerId;
    private List<CartItemResponseDto> items;
    private CartStatus status;
    private Double total;
    private LocalDateTime expiresAt;

    public CartResponseDto(Cart cart) {
        this.id = cart.getId();
        this.customerId = cart.getCustomerId();
        this.items = cart.getItems().stream().map(CartItemResponseDto::new).toList();
        this.status = cart.getStatus();
        this.total = cart.total();
        this.expiresAt = cart.getExpiresAt();
    }
}
