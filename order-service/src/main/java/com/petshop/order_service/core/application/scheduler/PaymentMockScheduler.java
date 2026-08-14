package com.petshop.order_service.core.application.scheduler;

import com.petshop.order_service.core.port.in.OrderPortIn;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simula a aprovação assíncrona de um gateway de pagamento real: aprova
 * periodicamente pedidos "aguardando pagamento" há mais tempo que o
 * atraso configurado (order.payment.mock-delay-seconds).
 */
@Component
@RequiredArgsConstructor
public class PaymentMockScheduler {

    private final OrderPortIn orderPortIn;

    @Scheduled(fixedDelayString = "${order.payment.check-interval-ms:10000}")
    public void checkPendingPayments() {
        orderPortIn.processPendingPayments();
    }
}
