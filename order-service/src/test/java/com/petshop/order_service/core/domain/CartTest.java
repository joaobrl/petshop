package com.petshop.order_service.core.domain;

import com.petshop.order_service.core.domain.enums.CartStatus;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartTest {

    @Nested
    class OpenFor {

        @Test
        void createsAnOpenCartWithGeneratedIdAndExpiration() {
            var customerId = UUID.randomUUID();

            var cart = Cart.openFor(customerId, 24);

            assertThat(cart.getId()).isNotNull();
            assertThat(cart.getCustomerId()).isEqualTo(customerId);
            assertThat(cart.getItems()).isEmpty();
            assertThat(cart.getStatus()).isEqualTo(CartStatus.OPEN);
            assertThat(cart.getCreatedAt()).isNotNull();
            assertThat(cart.getExpiresAt()).isAfter(LocalDateTime.now());
        }

        @Test
        void generatesDifferentIdsForDifferentCarts() {
            var a = Cart.openFor(UUID.randomUUID(), 24);
            var b = Cart.openFor(UUID.randomUUID(), 24);

            assertThat(a.getId()).isNotEqualTo(b.getId());
        }
    }

    @Nested
    class RenewExpiration {

        @Test
        void pushesExpirationForwardByGivenHours() {
            var cart = Cart.openFor(UUID.randomUUID(), 1);
            var before = cart.getExpiresAt();

            cart.renewExpiration(48);

            assertThat(cart.getExpiresAt()).isAfter(before);
            assertThat(cart.getExpiresAt()).isAfter(LocalDateTime.now().plusHours(47));
        }
    }

    @Nested
    class FindItem {

        @Test
        void returnsItemWhenProductPresent() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);

            assertThat(cart.findItem(1L)).isPresent();
            assertThat(cart.findItem(1L).get().getProductName()).isEqualTo("Racao");
        }

        @Test
        void returnsEmptyWhenProductAbsent() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);

            assertThat(cart.findItem(99L)).isEmpty();
        }
    }

    @Nested
    class AddOrIncrementItem {

        @Test
        void addsNewItemWhenProductNotYetInCart() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);

            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);

            assertThat(cart.getItems()).hasSize(1);
            var item = cart.getItems().get(0);
            assertThat(item.getProductId()).isEqualTo(1L);
            assertThat(item.getProductName()).isEqualTo("Racao");
            assertThat(item.getQuantity()).isEqualTo(2);
            assertThat(item.getUnitPrice()).isEqualTo(10.0);
            assertThat(item.isReserved()).isTrue();
        }

        @Test
        void addsNewUnreservedItemWhenReserveStockIsFalse() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);

            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, false);

            assertThat(cart.getItems().get(0).isReserved()).isFalse();
        }

        @Test
        void incrementsQuantityWhenProductAlreadyInCart() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);

            cart.addOrIncrementItem(1L, "Racao", 10.0, 3, true);

            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getItems().get(0).getQuantity()).isEqualTo(5);
        }

        @Test
        void marksItemAsReservedWhenIncrementedWithReservedTrue() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, false);

            cart.addOrIncrementItem(1L, "Racao", 10.0, 1, true);

            assertThat(cart.getItems().get(0).isReserved()).isTrue();
        }

        @Test
        void keepsItemReservedWhenIncrementedAgainWithReservedFalse() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);

            cart.addOrIncrementItem(1L, "Racao", 10.0, 1, false);

            assertThat(cart.getItems().get(0).isReserved()).isTrue();
        }
    }

    @Nested
    class RemoveItem {

        @Test
        void removesTheMatchingItem() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);
            cart.addOrIncrementItem(2L, "Brinquedo", 5.0, 1, true);

            cart.removeItem(1L);

            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getItems().get(0).getProductId()).isEqualTo(2L);
        }

        @Test
        void doesNothingWhenProductNotInCart() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);

            cart.removeItem(99L);

            assertThat(cart.getItems()).hasSize(1);
        }
    }

    @Nested
    class IsEmpty {

        @Test
        void isTrueForFreshCart() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);

            assertThat(cart.isEmpty()).isTrue();
        }

        @Test
        void isFalseAfterAddingAnItem() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);

            assertThat(cart.isEmpty()).isFalse();
        }
    }

    @Nested
    class Total {

        @Test
        void sumsSubtotalsOfAllItems() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);
            cart.addOrIncrementItem(2L, "Brinquedo", 5.0, 3, true);

            assertThat(cart.total()).isEqualTo(35.0);
        }

        @Test
        void isZeroForEmptyCart() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);

            assertThat(cart.total()).isZero();
        }
    }

    @Nested
    class IsExpired {

        @Test
        void isFalseWhenExpirationIsInTheFuture() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);

            assertThat(cart.isExpired()).isFalse();
        }

        @Test
        void isTrueWhenExpirationIsInThePast() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);
            cart.setExpiresAt(LocalDateTime.now().minusHours(1));

            assertThat(cart.isExpired()).isTrue();
        }

        @Test
        void isFalseWhenExpirationIsNull() {
            var cart = Cart.openFor(UUID.randomUUID(), 24);
            cart.setExpiresAt(null);

            assertThat(cart.isExpired()).isFalse();
        }
    }

    @Test
    void noArgsConstructorInitializesEmptyMutableItemsList() {
        var cart = new Cart();

        assertThat(cart.getItems()).isNotNull().isEmpty();
    }

    @Test
    void allArgsConstructorSetsEveryField() {
        var id = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var items = List.of(new CartItem(1L, "Racao", 1, 10.0, true));
        var createdAt = LocalDateTime.now().minusMinutes(5);
        var expiresAt = LocalDateTime.now().plusHours(1);

        var cart = new Cart(id, customerId, items, CartStatus.OPEN, createdAt, expiresAt);

        assertThat(cart.getId()).isEqualTo(id);
        assertThat(cart.getCustomerId()).isEqualTo(customerId);
        assertThat(cart.getItems()).isEqualTo(items);
        assertThat(cart.getStatus()).isEqualTo(CartStatus.OPEN);
        assertThat(cart.getCreatedAt()).isEqualTo(createdAt);
        assertThat(cart.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void settersUpdateFieldsDirectly() {
        var cart = new Cart();
        var id = UUID.randomUUID();
        var customerId = UUID.randomUUID();

        cart.setId(id);
        cart.setCustomerId(customerId);
        cart.setStatus(CartStatus.CHECKED_OUT);

        assertThat(cart.getId()).isEqualTo(id);
        assertThat(cart.getCustomerId()).isEqualTo(customerId);
        assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
    }
}
