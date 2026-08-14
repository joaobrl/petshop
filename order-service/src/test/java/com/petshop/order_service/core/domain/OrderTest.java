package com.petshop.order_service.core.domain;

import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.core.domain.enums.OrderStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    private Cart cartWithItems() {
        var cart = Cart.openFor(UUID.randomUUID(), 24);
        cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);
        cart.addOrIncrementItem(2L, "Brinquedo", 5.0, 1, true);
        return cart;
    }

    @Nested
    class FromCart {

        @Test
        void copiesCustomerIdItemsAndTotalFromCart() {
            var cart = cartWithItems();

            var order = Order.fromCart(cart);

            assertThat(order.getId()).isNotNull();
            assertThat(order.getCustomerId()).isEqualTo(cart.getCustomerId());
            assertThat(order.getItems()).hasSize(2);
            assertThat(order.getTotalAmount()).isEqualTo(cart.total());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
            assertThat(order.getCreatedAt()).isNotNull();
            assertThat(order.getPaidAt()).isNull();
            assertThat(order.getPickupReadyAt()).isNull();
        }

        @Test
        void generatesDifferentIdsForDifferentOrders() {
            var cart = cartWithItems();

            var a = Order.fromCart(cart);
            var b = Order.fromCart(cart);

            assertThat(a.getId()).isNotEqualTo(b.getId());
        }
    }

    @Nested
    class MarkAsPaid {

        @Test
        void setsStatusToPaidAndStampsPaidAt() {
            var order = Order.fromCart(cartWithItems());

            order.markAsPaid();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getPaidAt()).isNotNull();
        }
    }

    @Nested
    class SchedulePickup {

        @Test
        void setsPickupReadyAtInTheFuture() {
            var order = Order.fromCart(cartWithItems());

            order.schedulePickup(30);

            assertThat(order.getPickupReadyAt()).isAfter(LocalDateTime.now().plusMinutes(29));
        }
    }

    @Nested
    class MarkReadyForPickup {

        @Test
        void setsStatusToReadyForPickup() {
            var order = Order.fromCart(cartWithItems());
            order.markAsPaid();

            order.markReadyForPickup();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
        }
    }

    @Test
    void noArgsConstructorInitializesEmptyMutableItemsList() {
        var order = new Order();

        assertThat(order.getItems()).isNotNull().isEmpty();
    }

    @Test
    void allArgsConstructorSetsEveryField() {
        var id = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var items = List.of(new OrderItem(1L, "Racao", 1, 10.0));
        var createdAt = LocalDateTime.now().minusMinutes(10);
        var paidAt = LocalDateTime.now().minusMinutes(5);
        var pickupReadyAt = LocalDateTime.now().plusMinutes(30);

        var order = new Order(id, customerId, items, 10.0, OrderStatus.PAID, createdAt, paidAt, pickupReadyAt);

        assertThat(order.getId()).isEqualTo(id);
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getItems()).isEqualTo(items);
        assertThat(order.getTotalAmount()).isEqualTo(10.0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getCreatedAt()).isEqualTo(createdAt);
        assertThat(order.getPaidAt()).isEqualTo(paidAt);
        assertThat(order.getPickupReadyAt()).isEqualTo(pickupReadyAt);
    }

    @Test
    void settersUpdateFieldsDirectly() {
        var order = new Order();
        var id = UUID.randomUUID();

        order.setId(id);
        order.setTotalAmount(99.0);
        order.setStatus(OrderStatus.CANCELED);

        assertThat(order.getId()).isEqualTo(id);
        assertThat(order.getTotalAmount()).isEqualTo(99.0);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void fromCartWorksEvenIfCartStatusIsNotOpen() {
        var cart = cartWithItems();
        cart.setStatus(CartStatus.CHECKED_OUT);

        var order = Order.fromCart(cart);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }
}
