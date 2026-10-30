package com.petshop.order_service.core.application.scheduler;

import com.petshop.order_service.core.port.in.OrderPortIn;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cancela pedidos aguardando pagamento cuja reserva de estoque venceu
 * (order.payment.reservation-ttl-minutes, 30 min por padrão) e libera o
 * estoque reservado no checkout.
 */
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderPortIn orderPortIn;

    @Scheduled(fixedDelayString = "${order.payment.expiration-check-interval-ms:60000}")
    public void expireUnpaidOrders() {
        orderPortIn.expireUnpaidOrders();
    }
}
