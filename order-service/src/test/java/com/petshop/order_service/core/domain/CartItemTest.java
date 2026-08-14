package com.petshop.order_service.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartItemTest {

    @Test
    void noArgsConstructorLeavesFieldsAtDefaults() {
        var item = new CartItem();

        assertThat(item.getProductId()).isNull();
        assertThat(item.getProductName()).isNull();
        assertThat(item.getQuantity()).isNull();
        assertThat(item.getUnitPrice()).isNull();
        assertThat(item.isReserved()).isFalse();
    }

    @Test
    void allArgsConstructorSetsEveryField() {
        var item = new CartItem(1L, "Racao", 3, 10.0, true);

        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getProductName()).isEqualTo("Racao");
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getUnitPrice()).isEqualTo(10.0);
        assertThat(item.isReserved()).isTrue();
    }

    @Test
    void settersUpdateFieldsDirectly() {
        var item = new CartItem();

        item.setProductId(2L);
        item.setProductName("Brinquedo");
        item.setQuantity(5);
        item.setUnitPrice(15.5);
        item.setReserved(true);

        assertThat(item.getProductId()).isEqualTo(2L);
        assertThat(item.getProductName()).isEqualTo("Brinquedo");
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.getUnitPrice()).isEqualTo(15.5);
        assertThat(item.isReserved()).isTrue();
    }

    @Test
    void subtotalMultipliesUnitPriceByQuantity() {
        var item = new CartItem(1L, "Racao", 3, 10.0, true);

        assertThat(item.subtotal()).isEqualTo(30.0);
    }
}
