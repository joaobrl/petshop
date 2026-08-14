package com.petshop.order_service.core.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @Test
    void hasExactlyTheExpectedConstants() {
        assertThat(OrderStatus.values()).containsExactly(
                OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELED);
    }

    @Test
    void valueOfResolvesEachConstantByName() {
        assertThat(OrderStatus.valueOf("AWAITING_PAYMENT")).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        assertThat(OrderStatus.valueOf("PAID")).isEqualTo(OrderStatus.PAID);
        assertThat(OrderStatus.valueOf("READY_FOR_PICKUP")).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        assertThat(OrderStatus.valueOf("CANCELED")).isEqualTo(OrderStatus.CANCELED);
    }
}
