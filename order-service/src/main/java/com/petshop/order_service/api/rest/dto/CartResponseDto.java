package com.petshop.order_service.api.rest.dto;

import com.petshop.order_service.core.domain.CartView;
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
    /** Soma só dos itens que não estão indisponíveis. */
    private Double total;
    private LocalDateTime expiresAt;
    /** Nome dos produtos que saíram do carrinho nesta leitura por não estarem mais à venda. */
    private List<String> removedItems;

    public CartResponseDto(CartView view) {
        var cart = view.cart();
        this.id = cart.getId();
        this.customerId = cart.getCustomerId();
        this.items = cart.getItems().stream()
                .map(item -> new CartItemResponseDto(item, view.availabilityOf(item.getProductId())))
                .toList();
        this.status = cart.getStatus();
        this.total = view.total();
        this.expiresAt = cart.getExpiresAt();
        this.removedItems = view.removedItems();
    }
}
