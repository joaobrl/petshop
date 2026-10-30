package com.petshop.order_service.core.port.out;

import com.petshop.order_service.core.domain.Order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderPortOut {
    Order save(Order order);

    Optional<Order> findById(UUID id);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findAwaitingPaymentCreatedBefore(LocalDateTime threshold);

    /** Pedidos aguardando pagamento cuja reserva (Order#expiresAt) já venceu. */
    List<Order> findAwaitingPaymentExpiredBefore(LocalDateTime now);

    /**
     * Transição condicional AWAITING_PAYMENT → PAID (grava paidAt e
     * pickupReadyAt do pedido). Só acontece se o pedido ainda estiver
     * aguardando pagamento e não tiver vencido; devolve false quando outro
     * processo (ex.: a expiração) já mudou o status — quem perder não faz
     * mais nada.
     */
    boolean markAsPaidIfAwaitingPayment(Order order);

    /** Transição condicional AWAITING_PAYMENT → CANCELED; mesma regra do pagamento. */
    boolean cancelIfAwaitingPayment(UUID orderId);

    /**
     * Pedidos pagos cujo prazo de preparo (Order#pickupReadyAt) já venceu
     * — usados pelo PickupReadyScheduler.
     */
    List<Order> findPaidAndPickupDue(LocalDateTime now);
}
