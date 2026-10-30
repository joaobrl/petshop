package com.petshop.order_service.core.application.scheduler;

import com.petshop.order_service.core.port.in.CartPortIn;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Marca como abandonados (EXPIRED) os carrinhos abertos que passaram do
 * prazo (order.cart.abandoned-after-hours, 24h por padrão) sem finalizar a
 * compra e dispara o lembrete. O carrinho não reserva estoque, então não há
 * nada a liberar aqui.
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
