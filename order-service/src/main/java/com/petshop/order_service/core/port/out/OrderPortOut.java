package com.petshop.order_service.core.port.out;

import com.petshop.order_service.core.domain.Order;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderPortOut {
    Order save(Order order);

    Optional<Order> findById(UUID id);

    List<Order> findAwaitingPaymentCreatedBefore(LocalDateTime threshold);

    /**
     * Pedidos pagos cujo prazo de preparo (Order#pickupReadyAt) já venceu
     * — usados pelo PickupReadyScheduler.
     */
    List<Order> findPaidAndPickupDue(LocalDateTime now);
}
