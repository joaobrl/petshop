package com.petshop.order_service.api.rest.dto;

import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.enums.CartStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartResponseDtoTest {

    @Test
    void copiesAllFieldsIncludingMappedItemsAndTotal() {
        var cart = Cart.openFor(UUID.randomUUID(), 24);
        cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);

        var dto = new CartResponseDto(cart);

        assertThat(dto.getId()).isEqualTo(cart.getId());
        assertThat(dto.getCustomerId()).isEqualTo(cart.getCustomerId());
        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getStatus()).isEqualTo(CartStatus.OPEN);
        assertThat(dto.getTotal()).isEqualTo(20.0);
        assertThat(dto.getExpiresAt()).isEqualTo(cart.getExpiresAt());
    }

    @Test
    void handlesEmptyCart() {
        var cart = Cart.openFor(UUID.randomUUID(), 24);

        var dto = new CartResponseDto(cart);

        assertThat(dto.getItems()).isEmpty();
        assertThat(dto.getTotal()).isZero();
    }

    @Test
    void settersUpdateFieldsDirectly() {
        var dto = new CartResponseDto(Cart.openFor(UUID.randomUUID(), 24));
        var newId = UUID.randomUUID();

        dto.setId(newId);
        dto.setItems(List.of());
        dto.setStatus(CartStatus.EXPIRED);
        dto.setTotal(0.0);
        dto.setExpiresAt(LocalDateTime.now());

        assertThat(dto.getId()).isEqualTo(newId);
        assertThat(dto.getStatus()).isEqualTo(CartStatus.EXPIRED);
    }
}
