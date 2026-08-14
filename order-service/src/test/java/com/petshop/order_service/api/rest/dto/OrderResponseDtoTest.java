package com.petshop.order_service.api.rest.dto;

import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.core.domain.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderResponseDtoTest {

    private Order sampleOrder() {
        var cart = Cart.openFor(UUID.randomUUID(), 24);
        cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);
        var order = Order.fromCart(cart);
        order.setId(UUID.randomUUID());
        return order;
    }

    @Test
    void copiesAllFieldsIncludingMappedItems() {
        var order = sampleOrder();

        var dto = new OrderResponseDto(order);

        assertThat(dto.getId()).isEqualTo(order.getId());
        assertThat(dto.getCustomerId()).isEqualTo(order.getCustomerId());
        assertThat(dto.getItems()).hasSize(1);
        assertThat(dto.getTotalAmount()).isEqualTo(order.getTotalAmount());
        assertThat(dto.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(dto.getCreatedAt()).isEqualTo(order.getCreatedAt());
        assertThat(dto.getPaidAt()).isNull();
        assertThat(dto.getPickupReadyAt()).isNull();
    }

    @Test
    void reflectsPaidAndPickupTimestampsAfterLifecycleTransitions() {
        var order = sampleOrder();
        order.markAsPaid();
        order.schedulePickup(30);

        var dto = new OrderResponseDto(order);

        assertThat(dto.getPaidAt()).isEqualTo(order.getPaidAt());
        assertThat(dto.getPickupReadyAt()).isEqualTo(order.getPickupReadyAt());
        assertThat(dto.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void settersUpdateFieldsDirectly() {
        var dto = new OrderResponseDto(sampleOrder());
        var newId = UUID.randomUUID();

        dto.setId(newId);
        dto.setItems(List.of());
        dto.setStatus(OrderStatus.CANCELED);
        dto.setTotalAmount(0.0);

        assertThat(dto.getId()).isEqualTo(newId);
        assertThat(dto.getStatus()).isEqualTo(OrderStatus.CANCELED);
        assertThat(dto.getItems()).isEmpty();
    }
}
