package com.petshop.order_service.core.application.service;

import com.petshop.commons.exception.NotFoundException;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.CustomerInfo;
import com.petshop.order_service.core.domain.ProductStockInfo;
import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.core.port.out.CartPortOut;
import com.petshop.order_service.core.port.out.CustomerPortOut;
import com.petshop.order_service.core.port.out.NotificationPortOut;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartPortOut cartPortOut;
    @Mock
    private ProductStockPortOut productStockPortOut;
    @Mock
    private CustomerPortOut customerPortOut;
    @Mock
    private NotificationPortOut notificationPortOut;

    private CartService cartService;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartPortOut, productStockPortOut, customerPortOut, notificationPortOut);
        ReflectionTestUtils.setField(cartService, "reservationTtlHours", 24);
    }

    @Nested
    class AddItem {

        @Test
        void createsNewCartAndReservesStockWhenNoneOpenAndLoggedIn() {
            var customerId = UUID.randomUUID();
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.empty());
            when(productStockPortOut.reserve(1L, 2)).thenReturn(new ProductStockInfo(1L, "Racao", 10.0));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            var cart = cartService.addItem(customerId, 1L, 2, true);

            assertThat(cart.getCustomerId()).isEqualTo(customerId);
            assertThat(cart.getItems()).hasSize(1);
            assertThat(cart.getItems().get(0).isReserved()).isTrue();
            verify(productStockPortOut).reserve(1L, 2);
            verify(productStockPortOut, never()).getInfo(any());
        }

        @Test
        void usesInfoOnlyWithoutReservingWhenNotLoggedIn() {
            var customerId = UUID.randomUUID();
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.empty());
            when(productStockPortOut.getInfo(1L)).thenReturn(new ProductStockInfo(1L, "Racao", 10.0));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            var cart = cartService.addItem(customerId, 1L, 2, false);

            assertThat(cart.getItems().get(0).isReserved()).isFalse();
            verify(productStockPortOut).getInfo(1L);
            verify(productStockPortOut, never()).reserve(any(), anyInt());
        }

        @Test
        void reusesExistingOpenCart() {
            var customerId = UUID.randomUUID();
            var existingCart = Cart.openFor(customerId, 24);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(existingCart));
            when(productStockPortOut.reserve(1L, 1)).thenReturn(new ProductStockInfo(1L, "Racao", 10.0));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            var cart = cartService.addItem(customerId, 1L, 1, true);

            assertThat(cart.getId()).isEqualTo(existingCart.getId());
        }
    }

    @Nested
    class RemoveItem {

        @Test
        void releasesStockWhenItemWasReserved() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = cartService.removeItem(customerId, 1L);

            assertThat(result.getItems()).isEmpty();
            verify(productStockPortOut).release(1L, 2);
        }

        @Test
        void doesNotReleaseStockWhenItemWasNotReserved() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, false);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            cartService.removeItem(customerId, 1L);

            verify(productStockPortOut, never()).release(any(), anyInt());
        }

        @Test
        void throwsNotFoundWhenNoOpenCart() {
            var customerId = UUID.randomUUID();
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.removeItem(customerId, 1L))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void throwsNotFoundWhenItemNotInCart() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> cartService.removeItem(customerId, 99L))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class GetCart {

        @Test
        void returnsExistingOpenCart() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));

            assertThat(cartService.getCart(customerId)).isEqualTo(cart);
        }

        @Test
        void returnsFreshUnsavedCartWhenNoneOpen() {
            var customerId = UUID.randomUUID();
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.empty());

            var cart = cartService.getCart(customerId);

            assertThat(cart.getCustomerId()).isEqualTo(customerId);
            assertThat(cart.getStatus()).isEqualTo(CartStatus.OPEN);
            verify(cartPortOut, never()).save(any());
        }
    }

    @Nested
    class ExpireOldCarts {

        @Test
        void doesNothingWhenNoExpiredCarts() {
            when(cartPortOut.findExpiredOpenCarts()).thenReturn(List.of());

            cartService.expireOldCarts();

            verifyNoInteractions(notificationPortOut);
            verify(cartPortOut, never()).save(any());
        }

        @Test
        void releasesReservedItemsMarksExpiredAndSendsReminderWhenCustomerFound() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);
            cart.addOrIncrementItem(2L, "Brinquedo", 5.0, 1, false);
            when(cartPortOut.findExpiredOpenCarts()).thenReturn(List.of(cart));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
            when(customerPortOut.findCustomerById(customerId))
                    .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));

            cartService.expireOldCarts();

            verify(productStockPortOut).release(1L, 2);
            verify(productStockPortOut, never()).release(eq(2L), anyInt());
            assertThat(cart.getStatus()).isEqualTo(CartStatus.EXPIRED);
            verify(cartPortOut).save(cart);
            verify(notificationPortOut).sendCartReminder(cart, "maria@mail.com", "Maria");
        }

        @Test
        void logsAndSkipsReminderWhenCustomerNotFound() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);
            when(cartPortOut.findExpiredOpenCarts()).thenReturn(List.of(cart));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
            when(customerPortOut.findCustomerById(customerId)).thenReturn(Optional.empty());

            cartService.expireOldCarts();

            verify(notificationPortOut, never()).sendCartReminder(any(), any(), any());
        }

        @Test
        void doesNotSendReminderForEmptyExpiredCart() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            when(cartPortOut.findExpiredOpenCarts()).thenReturn(List.of(cart));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            cartService.expireOldCarts();

            assertThat(cart.getStatus()).isEqualTo(CartStatus.EXPIRED);
            verifyNoInteractions(customerPortOut, notificationPortOut);
        }
    }
}
