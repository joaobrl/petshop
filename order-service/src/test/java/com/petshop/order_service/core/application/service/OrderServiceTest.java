package com.petshop.order_service.core.application.service;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.CustomerInfo;
import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.core.domain.OrderItem;
import com.petshop.order_service.core.domain.ProductStockInfo;
import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.core.domain.enums.OrderStatus;
import com.petshop.order_service.core.port.out.CartPortOut;
import com.petshop.order_service.core.port.out.CustomerPortOut;
import com.petshop.order_service.core.port.out.NotificationPortOut;
import com.petshop.order_service.core.port.out.OrderPortOut;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import com.petshop.order_service.core.port.out.PurchaseHistoryPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderPortOut orderPortOut;
    @Mock
    private CartPortOut cartPortOut;
    @Mock
    private CustomerPortOut customerPortOut;
    @Mock
    private NotificationPortOut notificationPortOut;
    @Mock
    private ProductStockPortOut productStockPortOut;
    @Mock
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderPortOut, cartPortOut, customerPortOut, notificationPortOut,
                productStockPortOut, purchaseHistoryPortOut);
        ReflectionTestUtils.setField(orderService, "mockPaymentDelaySeconds", 30);
        ReflectionTestUtils.setField(orderService, "reservationTtlMinutes", 30);
        ReflectionTestUtils.setField(orderService, "pickupMinDelayMinutes", 1);
        ReflectionTestUtils.setField(orderService, "pickupMaxDelayMinutes", 60);
    }

    private static final String TOKEN = "Bearer test-token";

    private Cart cartWithItems(UUID customerId) {
        var cart = Cart.openFor(customerId, 24);
        cart.addOrIncrementItem(1L, "Racao", 10.0, 2);
        cart.addOrIncrementItem(2L, "Brinquedo", 5.0, 1);
        return cart;
    }

    private Order orderFor(UUID customerId) {
        var cart = cartWithItems(customerId);
        return Order.fromCartItems(customerId, cart.getItems(), 30);
    }

    private static ProductStockInfo onSale(Long id, String name) {
        return new ProductStockInfo(id, name, 10.0, true, "50+");
    }

    @Nested
    class Checkout {

        private final UUID customerId = UUID.randomUUID();

        private void customerExists() {
            when(customerPortOut.findCustomerById(customerId, TOKEN))
                    .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));
        }

        private void allOnSale() {
            when(productStockPortOut.getInfos(any(), eq(TOKEN))).thenReturn(Map.of(
                    1L, onSale(1L, "Racao"), 2L, onSale(2L, "Brinquedo")));
        }

        @Test
        void throwsBusinessRuleWhenNothingSelected() {
            assertThatThrownBy(() -> orderService.checkout(customerId, List.of(), null, TOKEN).order())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Selecione");
            assertThatThrownBy(() -> orderService.checkout(customerId, null, null, TOKEN).order())
                    .isInstanceOf(BusinessRuleException.class);

            verifyNoInteractions(cartPortOut, productStockPortOut);
        }

        @Test
        void throwsBusinessRuleWhenNoOpenCart() {
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.checkout(customerId, List.of(1L), null, TOKEN).order())
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void throwsBusinessRuleWhenSelectedItemIsNotInCart() {
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(cartWithItems(customerId)));

            assertThatThrownBy(() -> orderService.checkout(customerId, List.of(1L, 99L), null, TOKEN).order())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("99");

            verifyNoInteractions(productStockPortOut);
        }

        @Test
        void checksOutOnlySelectedItemsAndKeepsTheRestInOpenCart() {
            var cart = cartWithItems(customerId);
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(cart));
            customerExists();
            when(productStockPortOut.getInfos(List.of(1L), TOKEN)).thenReturn(Map.of(1L, onSale(1L, "Racao")));
            when(orderPortOut.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            var order = orderService.checkout(customerId, List.of(1L), null, TOKEN).order();

            assertThat(order.getItems()).extracting(OrderItem::getProductId).containsExactly(1L);
            assertThat(order.getTotalAmount()).isEqualTo(20.0);
            assertThat(order.getExpiresAt()).isEqualTo(order.getCreatedAt().plusMinutes(30));
            verify(productStockPortOut).reserve(1L, 2, TOKEN);
            verify(productStockPortOut, never()).reserve(eq(2L), anyInt(), any());
            assertThat(cart.productIds()).containsExactly(2L);
            assertThat(cart.getStatus()).isEqualTo(CartStatus.OPEN);
            verify(cartPortOut).save(cart);
        }

        @Test
        void marksCartCheckedOutWhenEveryItemIsSelectedAndNotifiesCustomer() {
            var cart = cartWithItems(customerId);
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(cart));
            customerExists();
            allOnSale();
            when(orderPortOut.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            var order = orderService.checkout(customerId, List.of(1L, 2L, 1L), null, TOKEN).order();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
            assertThat(order.getItems()).hasSize(2);
            assertThat(cart.isEmpty()).isTrue();
            assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
            verify(notificationPortOut).sendOrderAwaitingPayment(order, "maria@mail.com", "Maria");
        }

        @Test
        void reportsAllOutOfStockItemsInOneFailureInsteadOfJustTheFirst() {
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(cartWithItems(customerId)));
            customerExists();
            allOnSale();
            when(productStockPortOut.reserve(1L, 2, TOKEN))
                    .thenThrow(new BusinessRuleException("Estoque insuficiente para o produto 'Racao'"));
            when(productStockPortOut.reserve(2L, 1, TOKEN))
                    .thenThrow(new BusinessRuleException("Estoque insuficiente para o produto 'Brinquedo'"));

            assertThatThrownBy(() -> orderService.checkout(customerId, List.of(1L, 2L), null, TOKEN).order())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Racao")
                    .hasMessageContaining("Brinquedo");

            verify(productStockPortOut, never()).release(anyLong(), anyInt(), any());
            verify(orderPortOut, never()).save(any());
        }

        @Test
        void releasesItemsReservedInTheSameAttemptWhenAnotherItemFails() {
            var cart = cartWithItems(customerId);
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(cart));
            customerExists();
            allOnSale();
            when(productStockPortOut.reserve(1L, 2, TOKEN)).thenReturn(onSale(1L, "Racao"));
            when(productStockPortOut.reserve(2L, 1, TOKEN))
                    .thenThrow(new BusinessRuleException("Estoque insuficiente para o produto 'Brinquedo'"));

            assertThatThrownBy(() -> orderService.checkout(customerId, List.of(1L, 2L), null, TOKEN).order())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Estoque insuficiente para o produto 'Brinquedo'");

            verify(productStockPortOut).release(1L, 2, TOKEN);
            verify(orderPortOut, never()).save(any());
            verify(cartPortOut, never()).save(any());
            assertThat(cart.getItems()).hasSize(2);
        }

        @Test
        void rejectsDisabledOrMissingProductsWithoutReservingAnything() {
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(cartWithItems(customerId)));
            customerExists();
            when(productStockPortOut.getInfos(any(), eq(TOKEN)))
                    .thenReturn(Map.of(1L, new ProductStockInfo(1L, "Racao", 10.0, false, "50+")));

            assertThatThrownBy(() -> orderService.checkout(customerId, List.of(1L, 2L), null, TOKEN).order())
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Produto indisponível: 'Racao'")
                    .hasMessageContaining("Produto indisponível: 'Brinquedo'");

            verify(productStockPortOut, never()).reserve(any(), anyInt(), any());
        }

        @Test
        void throwsNotFoundBeforeReservingWhenCustomerDoesNotExist() {
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(cartWithItems(customerId)));
            when(customerPortOut.findCustomerById(customerId, TOKEN)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.checkout(customerId, List.of(1L), null, TOKEN).order())
                    .isInstanceOf(NotFoundException.class);

            verifyNoInteractions(productStockPortOut);
        }

        @Test
        void locksTheCartAndNeverUsesTheUnlockedLookup() {
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.checkout(customerId, List.of(1L), null, TOKEN))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Não há carrinho aberto");

            verify(cartPortOut, never()).findOpenCartByCustomerId(any());
            verify(orderPortOut, never()).findByIdempotencyKey(any());
        }

        @Test
        void storesTheIdempotencyKeyOnTheNewOrder() {
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(cartWithItems(customerId)));
            when(orderPortOut.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
            customerExists();
            allOnSale();
            when(orderPortOut.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = orderService.checkout(customerId, List.of(1L), "  key-1 ", TOKEN);

            assertThat(result.created()).isTrue();
            assertThat(result.order().getIdempotencyKey()).isEqualTo("key-1");
        }

        @Test
        void returnsExistingOrderWithoutReservingWhenKeyWasAlreadyUsed() {
            var existing = orderFor(customerId);
            // Carrinho já sem os itens: o primeiro checkout (mesma chave) commitou antes.
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.empty());
            when(orderPortOut.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

            var result = orderService.checkout(customerId, List.of(1L, 2L), "key-1", TOKEN);

            assertThat(result.created()).isFalse();
            assertThat(result.order()).isSameAs(existing);
            verifyNoInteractions(productStockPortOut, notificationPortOut);
            verify(orderPortOut, never()).save(any());
            verify(cartPortOut, never()).save(any());
        }

        @Test
        void rejectsKeyAlreadyUsedByAnotherCustomer() {
            when(cartPortOut.findOpenCartByCustomerIdForUpdate(customerId)).thenReturn(Optional.of(cartWithItems(customerId)));
            when(orderPortOut.findByIdempotencyKey("key-1")).thenReturn(Optional.of(orderFor(UUID.randomUUID())));

            assertThatThrownBy(() -> orderService.checkout(customerId, List.of(1L), "key-1", TOKEN))
                    .isInstanceOf(ConflictException.class);

            verifyNoInteractions(productStockPortOut);
        }

        @Test
        void rejectsOversizedKey() {
            assertThatThrownBy(() -> orderService.checkout(customerId, List.of(1L), "k".repeat(256), TOKEN))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(cartPortOut, productStockPortOut);
        }
    }

    @Nested
    class FindById {

        @Test
        void returnsOrderWhenFound() {
            var id = UUID.randomUUID();
            var order = new Order();
            order.setId(id);
            when(orderPortOut.findById(id)).thenReturn(Optional.of(order));

            assertThat(orderService.findById(id)).isEqualTo(order);
        }

        @Test
        void throwsNotFoundWhenMissing() {
            var id = UUID.randomUUID();
            when(orderPortOut.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.findById(id))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class ProcessPendingPayments {

        @Test
        void doesNothingWhenNoneAwaitingPayment() {
            when(orderPortOut.findAwaitingPaymentCreatedBefore(any())).thenReturn(List.of());

            orderService.processPendingPayments();

            verifyNoInteractions(notificationPortOut, purchaseHistoryPortOut, productStockPortOut);
        }

        @Test
        void approvesPaymentConfirmsStockAndNotifiesWhenCustomerFound() {
            var customerId = UUID.randomUUID();
            var order = orderFor(customerId);
            when(orderPortOut.findAwaitingPaymentCreatedBefore(any())).thenReturn(List.of(order));
            when(orderPortOut.markAsPaidIfAwaitingPayment(order)).thenReturn(true);
            when(customerPortOut.findCustomerById(customerId, null))
                    .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));

            orderService.processPendingPayments();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getPickupReadyAt()).isNotNull();
            verify(orderPortOut, never()).save(any());
            verify(productStockPortOut).confirm(1L, 2, null);
            verify(productStockPortOut).confirm(2L, 1, null);
            verify(notificationPortOut).sendPaymentConfirmed(order, "maria@mail.com", "Maria");
            verify(notificationPortOut).sendInvoiceIssued(order, "maria@mail.com", "Maria");
            verify(notificationPortOut).sendPickupWindow(order, "maria@mail.com", "Maria");
            verify(purchaseHistoryPortOut).publishOrderCompleted(order);
        }

        @Test
        void doesNothingWhenExpirationAlreadyWonTheOrder() {
            var order = orderFor(UUID.randomUUID());
            when(orderPortOut.findAwaitingPaymentCreatedBefore(any())).thenReturn(List.of(order));
            when(orderPortOut.markAsPaidIfAwaitingPayment(order)).thenReturn(false);

            orderService.processPendingPayments();

            verifyNoInteractions(productStockPortOut, notificationPortOut, purchaseHistoryPortOut, customerPortOut);
        }

        @Test
        void skipsNotificationButStillPublishesHistoryWhenCustomerNotFound() {
            var customerId = UUID.randomUUID();
            var order = orderFor(customerId);
            when(orderPortOut.findAwaitingPaymentCreatedBefore(any())).thenReturn(List.of(order));
            when(orderPortOut.markAsPaidIfAwaitingPayment(order)).thenReturn(true);
            when(customerPortOut.findCustomerById(customerId, null)).thenReturn(Optional.empty());

            orderService.processPendingPayments();

            verify(notificationPortOut, never()).sendPaymentConfirmed(any(), any(), any());
            verify(purchaseHistoryPortOut).publishOrderCompleted(order);
        }

        @Test
        void usesThresholdBasedOnConfiguredMockDelay() {
            when(orderPortOut.findAwaitingPaymentCreatedBefore(any())).thenReturn(List.of());

            orderService.processPendingPayments();

            var captor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(orderPortOut).findAwaitingPaymentCreatedBefore(captor.capture());
            assertThat(captor.getValue()).isBefore(LocalDateTime.now());
        }
    }

    @Nested
    class ExpireUnpaidOrders {

        @Test
        void doesNothingWhenNoneExpired() {
            when(orderPortOut.findAwaitingPaymentExpiredBefore(any())).thenReturn(List.of());

            orderService.expireUnpaidOrders();

            verifyNoInteractions(productStockPortOut);
        }

        @Test
        void cancelsExpiredOrderAndReleasesEveryItem() {
            var order = orderFor(UUID.randomUUID());
            when(orderPortOut.findAwaitingPaymentExpiredBefore(any())).thenReturn(List.of(order));
            when(orderPortOut.cancelIfAwaitingPayment(order.getId())).thenReturn(true);

            orderService.expireUnpaidOrders();

            verify(productStockPortOut).release(1L, 2, null);
            verify(productStockPortOut).release(2L, 1, null);
            verify(productStockPortOut, never()).confirm(any(), anyInt(), any());
            verifyNoInteractions(cartPortOut);
        }

        @Test
        void doesNothingWhenPaymentAlreadyWonTheOrder() {
            var order = orderFor(UUID.randomUUID());
            when(orderPortOut.findAwaitingPaymentExpiredBefore(any())).thenReturn(List.of(order));
            when(orderPortOut.cancelIfAwaitingPayment(order.getId())).thenReturn(false);

            orderService.expireUnpaidOrders();

            verifyNoInteractions(productStockPortOut);
        }
    }

    @Nested
    class ProcessPickupReadyOrders {

        @Test
        void doesNothingWhenNoneDue() {
            when(orderPortOut.findPaidAndPickupDue(any())).thenReturn(List.of());

            orderService.processPickupReadyOrders();

            verifyNoInteractions(notificationPortOut);
        }

        @Test
        void marksReadyAndNotifiesWhenCustomerFound() {
            var customerId = UUID.randomUUID();
            var order = orderFor(customerId);
            order.markAsPaid();
            when(orderPortOut.findPaidAndPickupDue(any())).thenReturn(List.of(order));
            when(customerPortOut.findCustomerById(customerId, null))
                    .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));

            orderService.processPickupReadyOrders();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
            verify(orderPortOut).save(order);
            verify(notificationPortOut).sendOrderReadyForPickup(order, "maria@mail.com", "Maria");
        }

        @Test
        void skipsNotificationWhenCustomerNotFound() {
            var customerId = UUID.randomUUID();
            var order = orderFor(customerId);
            order.markAsPaid();
            when(orderPortOut.findPaidAndPickupDue(any())).thenReturn(List.of(order));
            when(customerPortOut.findCustomerById(customerId, null)).thenReturn(Optional.empty());

            orderService.processPickupReadyOrders();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
            verify(notificationPortOut, never()).sendOrderReadyForPickup(any(), any(), any());
        }
    }
}
