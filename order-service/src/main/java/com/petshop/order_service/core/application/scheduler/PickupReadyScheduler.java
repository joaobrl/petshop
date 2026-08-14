package com.petshop.order_service.core.application.scheduler;

import com.petshop.order_service.core.port.in.OrderPortIn;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Roda periodicamente e avisa por e-mail (marcando READY_FOR_PICKUP) todo
 * pedido pago cujo prazo de preparo sorteado (ver OrderService,
 * order.pickup.min/max-delay-minutes) já venceu.
 */
@Component
@RequiredArgsConstructor
public class PickupReadyScheduler {

    private final OrderPortIn orderPortIn;

    @Scheduled(fixedDelayString = "${order.pickup.check-interval-ms:10000}")
    public void checkOrdersReadyForPickup() {
        orderPortIn.processPickupReadyOrders();
    }
}
