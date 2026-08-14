package com.petshop.order_service.core.port.in;

import com.petshop.order_service.core.domain.Order;

import java.util.UUID;

public interface OrderPortIn {

    Order checkout(UUID customerId);

    Order findById(UUID id);

    void processPendingPayments();

    /**
     * Chamado periodicamente pelo PickupReadyScheduler: avisa por e-mail
     * (e marca como READY_FOR_PICKUP) qualquer pedido pago cujo prazo de
     * preparo sorteado já venceu.
     */
    void processPickupReadyOrders();
}
