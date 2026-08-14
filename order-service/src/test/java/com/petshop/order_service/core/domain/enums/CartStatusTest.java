package com.petshop.order_service.core.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartStatusTest {

    @Test
    void hasExactlyTheExpectedConstants() {
        assertThat(CartStatus.values()).containsExactly(CartStatus.OPEN, CartStatus.CHECKED_OUT, CartStatus.EXPIRED);
    }

    @Test
    void valueOfResolvesEachConstantByName() {
        assertThat(CartStatus.valueOf("OPEN")).isEqualTo(CartStatus.OPEN);
        assertThat(CartStatus.valueOf("CHECKED_OUT")).isEqualTo(CartStatus.CHECKED_OUT);
        assertThat(CartStatus.valueOf("EXPIRED")).isEqualTo(CartStatus.EXPIRED);
    }
}
