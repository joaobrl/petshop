package com.petshop.order_service.core.application.service;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.CustomerInfo;
import com.petshop.order_service.core.domain.Order;
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
        ReflectionTestUtils.setField(orderService, "pickupMinDelayMinutes", 1);
        ReflectionTestUtils.setField(orderService, "pickupMaxDelayMinutes", 60);
    }

    private Cart cartWithItems(UUID customerId, boolean allReserved) {
        var cart = Cart.openFor(customerId, 24);
        cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);
        cart.addOrIncrementItem(2L, "Brinquedo", 5.0, 1, allReserved);
        return cart;
    }

    @Nested
    class Checkout {

        @Test
        void throwsBusinessRuleWhenNoOpenCart() {
            var customerId = UUID.randomUUID();
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.checkout(customerId))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        void throwsBusinessRuleWhenCartIsEmpty() {
            var customerId = UUID.randomUUID();
            var cart = Cart.openFor(customerId, 24);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> orderService.checkout(customerId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("vazio");
        }

        @Test
        void reservesOnlyItemsNotYetReservedBeforeCheckingOut() {
            var customerId = UUID.randomUUID();
            var cart = cartWithItems(customerId, false);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(orderPortOut.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(productStockPortOut.reserve(2L, 1)).thenReturn(new ProductStockInfo(2L, "Brinquedo", 5.0));
            when(customerPortOut.findCustomerById(customerId))
                    .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));

            orderService.checkout(customerId);

            verify(productStockPortOut).reserve(2L, 1);
            verify(productStockPortOut, never()).reserve(eq(1L), anyInt());
        }

        @Test
        void createsOrderMarksCartCheckedOutAndNotifiesCustomer() {
            var customerId = UUID.randomUUID();
            var cart = cartWithItems(customerId, true);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(orderPortOut.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(customerPortOut.findCustomerById(customerId))
                    .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));

            var order = orderService.checkout(customerId);

            assertThat(order.getCustomerId()).isEqualTo(customerId);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
            assertThat(cart.getStatus()).isEqualTo(CartStatus.CHECKED_OUT);
            verify(cartPortOut).save(cart);
            verify(notificationPortOut).sendOrderAwaitingPayment(order, "maria@mail.com", "Maria");
        }

        @Test
        void throwsNotFoundWhenCustomerDoesNotExist() {
            var customerId = UUID.randomUUID();
            var cart = cartWithItems(customerId, true);
            when(cartPortOut.findOpenCartByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(orderPortOut.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
            when(customerPortOut.findCustomerById(customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> orderService.checkout(customerId))
                    .isInstanceOf(NotFoundException.class);
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
            var order = Order.fromCart(cartWithItems(customerId, true));
            when(orderPortOut.findAwaitingPaymentCreatedBefore(any())).thenReturn(List.of(order));
            when(customerPortOut.findCustomerById(customerId))
                    .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));

            orderService.processPendingPayments();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            assertThat(order.getPickupReadyAt()).isNotNull();
            verify(orderPortOut).save(order);
            verify(productStockPortOut).confirm(1L, 2);
            verify(productStockPortOut).confirm(2L, 1);
            verify(notificationPortOut).sendPaymentConfirmed(order, "maria@mail.com", "Maria");
            verify(notificationPortOut).sendInvoiceIssued(order, "maria@mail.com", "Maria");
            verify(notificationPortOut).sendPickupWindow(order, "maria@mail.com", "Maria");
            verify(purchaseHistoryPortOut).publishOrderCompleted(order);
        }

        @Test
        void skipsNotificationButStillPublishesHistoryWhenCustomerNotFound() {
            var customerId = UUID.randomUUID();
            var order = Order.fromCart(cartWithItems(customerId, true));
            when(orderPortOut.findAwaitingPaymentCreatedBefore(any())).thenReturn(List.of(order));
            when(customerPortOut.findCustomerById(customerId)).thenReturn(Optional.empty());

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
            var order = Order.fromCart(cartWithItems(customerId, true));
            order.markAsPaid();
            when(orderPortOut.findPaidAndPickupDue(any())).thenReturn(List.of(order));
            when(customerPortOut.findCustomerById(customerId))
                    .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));

            orderService.processPickupReadyOrders();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
            verify(orderPortOut).save(order);
            verify(notificationPortOut).sendOrderReadyForPickup(order, "maria@mail.com", "Maria");
        }

        @Test
        void skipsNotificationWhenCustomerNotFound() {
            var customerId = UUID.randomUUID();
            var order = Order.fromCart(cartWithItems(customerId, true));
            order.markAsPaid();
            when(orderPortOut.findPaidAndPickupDue(any())).thenReturn(List.of(order));
            when(customerPortOut.findCustomerById(customerId)).thenReturn(Optional.empty());

            orderService.processPickupReadyOrders();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.READY_FOR_PICKUP);
            verify(notificationPortOut, never()).sendOrderReadyForPickup(any(), any(), any());
        }
    }
}
