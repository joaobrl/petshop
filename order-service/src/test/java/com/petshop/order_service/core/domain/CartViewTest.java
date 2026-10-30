package com.petshop.order_service.core.domain;

import com.petshop.order_service.core.domain.enums.StockAvailability;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartViewTest {

    private Cart cartWithTwoItems() {
        var cart = Cart.openFor(UUID.randomUUID(), 24);
        cart.addOrIncrementItem(1L, "Racao", 10.0, 2);
        cart.addOrIncrementItem(2L, "Brinquedo", 5.0, 1);
        return cart;
    }

    @Test
    void totalLeavesUnavailableItemsOut() {
        var view = new CartView(cartWithTwoItems(),
                Map.of(1L, StockAvailability.LOW, 2L, StockAvailability.UNAVAILABLE), List.of());

        assertThat(view.total()).isEqualTo(20.0);
        assertThat(view.isUnavailable(2L)).isTrue();
        assertThat(view.availabilityOf(1L)).isEqualTo(StockAvailability.LOW);
    }

    @Test
    void withoutAvailabilityCountsEveryItem() {
        var view = CartView.withoutAvailability(cartWithTwoItems());

        assertThat(view.total()).isEqualTo(25.0);
        assertThat(view.availabilityOf(1L)).isNull();
        assertThat(view.removedItems()).isEmpty();
    }
}
