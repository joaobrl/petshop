package com.petshop.order_service.core.application.service;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.CustomerInfo;
import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.core.port.in.OrderPortIn;
import com.petshop.order_service.core.port.out.CartPortOut;
import com.petshop.order_service.core.port.out.CustomerPortOut;
import com.petshop.order_service.core.port.out.NotificationPortOut;
import com.petshop.order_service.core.port.out.OrderPortOut;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import com.petshop.order_service.core.port.out.PurchaseHistoryPortOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService implements OrderPortIn {

    private final OrderPortOut orderPortOut;
    private final CartPortOut cartPortOut;
    private final CustomerPortOut customerPortOut;
    private final NotificationPortOut notificationPortOut;
    private final ProductStockPortOut productStockPortOut;
    private final PurchaseHistoryPortOut purchaseHistoryPortOut;

    @Value("${order.payment.mock-delay-seconds:30}")
    private int mockPaymentDelaySeconds;

    // Prazo de preparo sorteado nessa faixa a cada pedido pago, só para
    // fins de teste (em produção seria fixo/real). O e-mail ao cliente
    // continua fixo ("até 1h").
    @Value("${order.pickup.min-delay-minutes:1}")
    private int pickupMinDelayMinutes;

    @Value("${order.pickup.max-delay-minutes:60}")
    private int pickupMaxDelayMinutes;

    @Override
    @Transactional
    public Order checkout(UUID customerId) {
        var cart = cartPortOut.findOpenCartByCustomerId(customerId)
                .orElseThrow(() -> new BusinessRuleException("Não há carrinho aberto para o cliente " + customerId));

        if (cart.isEmpty()) {
            throw new BusinessRuleException("Não é possível finalizar um carrinho vazio.");
        }

        // Itens adicionados sem login não reservam estoque na hora; garante
        // aqui que tudo esteja reservado antes de virar pedido de verdade.
        ensureAllItemsReserved(cart);

        var order = Order.fromCart(cart);
        var savedOrder = orderPortOut.save(order);

        cart.setStatus(CartStatus.CHECKED_OUT);
        cartPortOut.save(cart);

        var customer = requireCustomer(customerId);
        notificationPortOut.sendOrderAwaitingPayment(savedOrder, customer.email(), customer.name());

        log.info("Pedido {} criado para o cliente {} (total: {}), aguardando pagamento.",
                savedOrder.getId(), customerId, savedOrder.getTotalAmount());
        return savedOrder;
    }

    private void ensureAllItemsReserved(Cart cart) {
        for (var item : cart.getItems()) {
            if (!item.isReserved()) {
                productStockPortOut.reserve(item.getProductId(), item.getQuantity());
                item.setReserved(true);
            }
        }
    }

    @Override
    public Order findById(UUID id) {
        return orderPortOut.findById(id)
                .orElseThrow(() -> new NotFoundException("Pedido", id));
    }

    // Chamado periodicamente pelo PaymentMockScheduler: aprova pedidos
    // aguardando pagamento há mais tempo que o atraso configurado,
    // simulando um webhook assíncrono de gateway real.
    @Override
    @Transactional
    public void processPendingPayments() {
        var threshold = LocalDateTime.now().minusSeconds(mockPaymentDelaySeconds);
        var pendingOrders = orderPortOut.findAwaitingPaymentCreatedBefore(threshold);

        for (var order : pendingOrders) {
            order.markAsPaid();

            int pickupDelayMinutes = ThreadLocalRandom.current()
                    .nextInt(pickupMinDelayMinutes, pickupMaxDelayMinutes + 1);
            order.schedulePickup(pickupDelayMinutes);
            orderPortOut.save(order);

            for (var item : order.getItems()) {
                productStockPortOut.confirm(item.getProductId(), item.getQuantity());
            }

            customerPortOut.findCustomerById(order.getCustomerId()).ifPresentOrElse(
                    customer -> {
                        notificationPortOut.sendPaymentConfirmed(order, customer.email(), customer.name());
                        notificationPortOut.sendInvoiceIssued(order, customer.email(), customer.name());
                        notificationPortOut.sendPickupWindow(order, customer.email(), customer.name());
                    },
                    () -> log.warn("Cliente {} não encontrado; e-mails do pedido {} não enviados.",
                            order.getCustomerId(), order.getId())
            );

            purchaseHistoryPortOut.publishOrderCompleted(order);

            log.info("Pagamento simulado aprovado para o pedido {}. Retirada estimada em {} min (prazo de teste).",
                    order.getId(), pickupDelayMinutes);
        }
    }

    // Chamado periodicamente pelo PickupReadyScheduler: pedido pago cujo
    // prazo de preparo sorteado já passou vira "pronto pra retirada".
    @Override
    @Transactional
    public void processPickupReadyOrders() {
        var dueOrders = orderPortOut.findPaidAndPickupDue(LocalDateTime.now());

        for (var order : dueOrders) {
            order.markReadyForPickup();
            orderPortOut.save(order);

            customerPortOut.findCustomerById(order.getCustomerId()).ifPresentOrElse(
                    customer -> notificationPortOut.sendOrderReadyForPickup(order, customer.email(), customer.name()),
                    () -> log.warn("Cliente {} não encontrado; e-mail de retirada do pedido {} não enviado.",
                            order.getCustomerId(), order.getId())
            );

            log.info("Pedido {} pronto para retirada.", order.getId());
        }
    }

    private CustomerInfo requireCustomer(UUID customerId) {
        return customerPortOut.findCustomerById(customerId)
                .orElseThrow(() -> new NotFoundException("Cliente", customerId));
    }
}
