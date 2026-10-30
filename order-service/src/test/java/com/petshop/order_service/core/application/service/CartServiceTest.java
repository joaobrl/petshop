package com.petshop.order_service.core.application.service;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.CustomerInfo;
import com.petshop.order_service.core.domain.ProductStockInfo;
import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.core.domain.enums.StockAvailability;
import com.petshop.order_service.core.port.out.CartPortOut;
import com.petshop.order_service.core.port.out.CustomerPortOut;
import com.petshop.order_service.core.port.out.NotificationPortOut;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    private static final String TOKEN = "Bearer test-token";

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartPortOut, productStockPortOut, customerPortOut, notificationPortOut);
        ReflectionTestUtils.setField(cartService, "abandonedAfterHours", 24);
    }

    private static ProductStockInfo info(Long id, String name, String stockRange) {
        return new ProductStockInfo(id, name, 10.0, true, stockRange);
    }

    private static ProductStockInfo disabled(Long id, String name) {
        return new ProductStockInfo(id, name, 10.0, false, "50+");
    }

    @Nested
    class AddItem {

        @Test
        void createsNewCartWithoutReservingStock() {
            var customerId = UUID.randomUUID();
            when(productStockPortOut.getInfo(1L, TOKEN)).thenReturn(info(1L, "Racao", "50+"));
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.empty());
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productStockPortOut.getInfos(List.of(1L), TOKEN)).thenReturn(Map.of(1L, info(1L, "Racao", "50+")));

            var view = cartService.addItem(customerId, 1L, 2, TOKEN);

            assertThat(view.cart().getCustomerId()).isEqualTo(customerId);
            assertThat(view.cart().getItems()).hasSize(1);
            assertThat(view.availabilityOf(1L)).isEqualTo(StockAvailability.AVAILABLE);
            verify(productStockPortOut, never()).reserve(any(), anyInt(), any());
        }

        @Test
        void acceptsQuantityAboveAvailableWhenProductHasSomeStock() {
            var customerId = UUID.randomUUID();
            when(productStockPortOut.getInfo(1L, TOKEN)).thenReturn(info(1L, "Racao", "LOW"));
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.empty());
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productStockPortOut.getInfos(List.of(1L), TOKEN)).thenReturn(Map.of(1L, info(1L, "Racao", "LOW")));

            var view = cartService.addItem(customerId, 1L, 10, TOKEN);

            assertThat(view.cart().getItems().get(0).getQuantity()).isEqualTo(10);
            assertThat(view.availabilityOf(1L)).isEqualTo(StockAvailability.LOW);
        }

        @Test
        void rejectsProductWithoutStock() {
            when(productStockPortOut.getInfo(1L, TOKEN)).thenReturn(info(1L, "Racao", null));

            assertThatThrownBy(() -> cartService.addItem(UUID.randomUUID(), 1L, 1, TOKEN))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Produto sem estoque");

            verify(cartPortOut, never()).save(any());
        }

        @Test
        void rejectsDisabledProductAsNotFound() {
            when(productStockPortOut.getInfo(1L, TOKEN)).thenReturn(disabled(1L, "Racao"));

            assertThatThrownBy(() -> cartService.addItem(UUID.randomUUID(), 1L, 1, TOKEN))
                    .isInstanceOf(NotFoundException.class);

            verify(cartPortOut, never()).save(any());
        }

        @Test
        void reusesExistingOpenCart() {
            var customerId = UUID.randomUUID();
            var existingCart = Cart.openFor(customerId, 24);
            when(productStockPortOut.getInfo(1L, TOKEN)).thenReturn(info(1L, "Racao", "50+"));
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(existingCart));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productStockPortOut.getInfos(any(), any())).thenReturn(Map.of(1L, info(1L, "Racao", "50+")));

            var view = cartService.addItem(customerId, 1L, 1, TOKEN);

            assertThat(view.cart().getId()).isEqualTo(existingCart.getId());
        }
    }

    @Nested
    class RemoveItem {

        @Test
        void removesItemWithoutTouchingStock() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = cartService.removeItem(customerId, 1L, TOKEN);

            assertThat(result.cart().getItems()).isEmpty();
            verify(productStockPortOut, never()).release(any(), anyInt(), any());
            verify(productStockPortOut, never()).getInfos(any(), any());
        }

        @Test
        void throwsNotFoundWhenNoOpenCart() {
            var customerId = UUID.randomUUID();
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> cartService.removeItem(customerId, 1L, TOKEN))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void throwsNotFoundWhenItemNotInCart() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> cartService.removeItem(customerId, 99L, TOKEN))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class GetCart {

        private Cart cartWith(UUID customerId, int items) {
            var cart = Cart.openFor(customerId, 24);
            for (long id = 1; id <= items; id++) {
                cart.addOrIncrementItem(id, "Produto " + id, 10.0, 1);
            }
            return cart;
        }

        @Test
        @SuppressWarnings("unchecked")
        void queriesAllProductsInASingleCall() {
            var customerId = UUID.randomUUID();
            var cart = cartWith(customerId, 5);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(productStockPortOut.getInfos(any(), eq(TOKEN))).thenReturn(Map.of(
                    1L, info(1L, "Produto 1", "50+"), 2L, info(2L, "Produto 2", "20+"),
                    3L, info(3L, "Produto 3", "LOW"), 4L, info(4L, "Produto 4", null),
                    5L, info(5L, "Produto 5", "50+")));

            var view = cartService.getCart(customerId, TOKEN);

            ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(productStockPortOut, times(1)).getInfos(captor.capture(), eq(TOKEN));
            assertThat(captor.getValue()).containsExactly(1L, 2L, 3L, 4L, 5L);
            verify(productStockPortOut, never()).getInfo(any(), any());
            assertThat(view.availabilityOf(2L)).isEqualTo(StockAvailability.AVAILABLE);
            assertThat(view.availabilityOf(3L)).isEqualTo(StockAvailability.LOW);
            assertThat(view.availabilityOf(4L)).isEqualTo(StockAvailability.UNAVAILABLE);
        }

        @Test
        void keepsOutOfStockItemButLeavesItOutOfTheTotal() {
            var customerId = UUID.randomUUID();
            var cart = cartWith(customerId, 2);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(productStockPortOut.getInfos(any(), any())).thenReturn(Map.of(
                    1L, info(1L, "Produto 1", "50+"), 2L, info(2L, "Produto 2", null)));

            var view = cartService.getCart(customerId, TOKEN);

            assertThat(view.cart().getItems()).hasSize(2);
            assertThat(view.total()).isEqualTo(10.0);
            verify(cartPortOut, never()).save(any());
        }

        @Test
        void removesAndPersistsDisabledOrMissingProducts() {
            var customerId = UUID.randomUUID();
            var cart = cartWith(customerId, 3);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(productStockPortOut.getInfos(any(), any())).thenReturn(Map.of(
                    1L, info(1L, "Produto 1", "50+"), 2L, disabled(2L, "Produto 2")));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            var view = cartService.getCart(customerId, TOKEN);

            assertThat(view.cart().productIds()).containsExactly(1L);
            assertThat(view.removedItems()).containsExactly("Produto 2", "Produto 3");
            verify(cartPortOut).save(cart);
        }

        @Test
        void returnsCartUntouchedWhenProductServiceIsDown() {
            var customerId = UUID.randomUUID();
            var cart = cartWith(customerId, 2);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(productStockPortOut.getInfos(any(), any())).thenThrow(new BusinessRuleException("fora do ar"));

            var view = cartService.getCart(customerId, TOKEN);

            assertThat(view.cart().getItems()).hasSize(2);
            assertThat(view.availabilityOf(1L)).isNull();
            assertThat(view.removedItems()).isEmpty();
            verify(cartPortOut, never()).save(any());
        }

        @Test
        void returnsFreshUnsavedCartWithoutCallingProductServiceWhenNoneOpen() {
            var customerId = UUID.randomUUID();
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.empty());

            var view = cartService.getCart(customerId, TOKEN);

            assertThat(view.cart().getCustomerId()).isEqualTo(customerId);
            assertThat(view.cart().getStatus()).isEqualTo(CartStatus.OPEN);
            verify(cartPortOut, never()).save(any());
            verifyNoInteractions(productStockPortOut);
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
        void marksExpiredAndSendsReminderWithoutReleasingStock() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2);
            cart.addOrIncrementItem(2L, "Brinquedo", 5.0, 1);
            when(cartPortOut.findExpiredOpenCarts()).thenReturn(List.of(cart));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
            when(customerPortOut.findCustomerById(customerId, null))
                    .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));

            cartService.expireOldCarts();

            verifyNoInteractions(productStockPortOut);
            assertThat(cart.getStatus()).isEqualTo(CartStatus.EXPIRED);
            verify(cartPortOut).save(cart);
            verify(notificationPortOut).sendCartReminder(cart, "maria@mail.com", "Maria");
        }

        @Test
        void logsAndSkipsReminderWhenCustomerNotFound() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            cart.addOrIncrementItem(1L, "Racao", 10.0, 2);
            when(cartPortOut.findExpiredOpenCarts()).thenReturn(List.of(cart));
            when(cartPortOut.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
            when(customerPortOut.findCustomerById(customerId, null)).thenReturn(Optional.empty());

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
