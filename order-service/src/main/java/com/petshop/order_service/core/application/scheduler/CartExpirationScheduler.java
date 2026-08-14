package com.petshop.order_service.core.application.scheduler;

import com.petshop.order_service.core.port.in.CartPortIn;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Libera o estoque reservado por carrinhos abertos que passaram do prazo de
 * reserva (order.cart.reservation-ttl-hours, 24h por padrão) sem finalizar
 * a compra.
 */
@Component
@RequiredArgsConstructor
public class CartExpirationScheduler {

    private final CartPortIn cartPortIn;

    @Scheduled(fixedDelayString = "${order.cart.expiration-check-interval-ms:600000}")
    public void expireOldCarts() {
        cartPortIn.expireOldCarts();
    }
}
