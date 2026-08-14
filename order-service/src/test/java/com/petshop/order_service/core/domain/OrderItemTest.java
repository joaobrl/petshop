package com.petshop.order_service.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderItemTest {

    @Test
    void noArgsConstructorLeavesFieldsNull() {
        var item = new OrderItem();

        assertThat(item.getProductId()).isNull();
        assertThat(item.getProductName()).isNull();
        assertThat(item.getQuantity()).isNull();
        assertThat(item.getUnitPrice()).isNull();
    }

    @Test
    void allArgsConstructorSetsEveryField() {
        var item = new OrderItem(1L, "Racao", 3, 10.0);

        assertThat(item.getProductId()).isEqualTo(1L);
        assertThat(item.getProductName()).isEqualTo("Racao");
        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getUnitPrice()).isEqualTo(10.0);
    }

    @Test
    void copyConstructorCopiesFieldsFromCartItem() {
        var cartItem = new CartItem(1L, "Racao", 3, 10.0, true);

        var orderItem = new OrderItem(cartItem);

        assertThat(orderItem.getProductId()).isEqualTo(1L);
        assertThat(orderItem.getProductName()).isEqualTo("Racao");
        assertThat(orderItem.getQuantity()).isEqualTo(3);
        assertThat(orderItem.getUnitPrice()).isEqualTo(10.0);
    }

    @Test
    void settersUpdateFieldsDirectly() {
        var item = new OrderItem();

        item.setProductId(2L);
        item.setProductName("Brinquedo");
        item.setQuantity(5);
        item.setUnitPrice(15.5);

        assertThat(item.getProductId()).isEqualTo(2L);
        assertThat(item.getProductName()).isEqualTo("Brinquedo");
        assertThat(item.getQuantity()).isEqualTo(5);
        assertThat(item.getUnitPrice()).isEqualTo(15.5);
    }

    @Test
    void subtotalMultipliesUnitPriceByQuantity() {
        var item = new OrderItem(1L, "Racao", 3, 10.0);

        assertThat(item.subtotal()).isEqualTo(30.0);
    }
}
