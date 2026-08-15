package com.petshop.order_service.core.application.service;

import com.petshop.commons.exception.NotFoundException;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.core.port.in.CartPortIn;
import com.petshop.order_service.core.port.out.CartPortOut;
import com.petshop.order_service.core.port.out.CustomerPortOut;
import com.petshop.order_service.core.port.out.NotificationPortOut;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService implements CartPortIn {

    private final CartPortOut cartPortOut;
    private final ProductStockPortOut productStockPortOut;
    private final CustomerPortOut customerPortOut;
    private final NotificationPortOut notificationPortOut;

    @Value("${order.cart.reservation-ttl-hours:24}")
    private int reservationTtlHours;

    @Override
    @Transactional
    public Cart addItem(UUID customerId, Long productId, int quantity, boolean reserveStock) {
        var cart = cartPortOut.findOpenCartByCustomerId(customerId)
                .orElseGet(() -> Cart.openFor(customerId, reservationTtlHours));

        var productInfo = reserveStock
                ? productStockPortOut.reserve(productId, quantity)
                : productStockPortOut.getInfo(productId);

        cart.addOrIncrementItem(productId, productInfo.name(), productInfo.price(), quantity, reserveStock);
        cart.renewExpiration(reservationTtlHours);

        var saved = cartPortOut.save(cart);
        log.info("Item {} (x{}) adicionado/atualizado no carrinho do cliente {} (reservado={})",
                productId, quantity, customerId, reserveStock);
        return saved;
    }

    @Override
    @Transactional
    public Cart removeItem(UUID customerId, Long productId) {
        var cart = cartPortOut.findOpenCartByCustomerId(customerId)
                .orElseThrow(() -> new NotFoundException("Carrinho do cliente", customerId));

        var item = cart.findItem(productId)
                .orElseThrow(() -> new NotFoundException("Item do carrinho para o produto", productId));

        if (item.isReserved()) {
            productStockPortOut.release(productId, item.getQuantity());
        }
        cart.removeItem(productId);

        var saved = cartPortOut.save(cart);
        log.info("Item {} removido do carrinho do cliente {}", productId, customerId);
        return saved;
    }

    @Override
    public Cart getCart(UUID customerId) {
        return cartPortOut.findOpenCartByCustomerId(customerId)
                .orElseGet(() -> Cart.openFor(customerId, reservationTtlHours));
    }

    @Override
    @Transactional
    public void expireOldCarts() {
        var expiredCarts = cartPortOut.findExpiredOpenCarts();

        for (var cart : expiredCarts) {
            boolean hadItems = !cart.isEmpty();

            for (var item : cart.getItems()) {
                if (item.isReserved()) {
                    productStockPortOut.release(item.getProductId(), item.getQuantity());
                }
            }
            cart.setStatus(CartStatus.EXPIRED);
            cartPortOut.save(cart);
            log.info("Carrinho {} do cliente {} expirou; estoque reservado foi liberado.", cart.getId(), cart.getCustomerId());

            if (hadItems) {
                sendAbandonedCartReminder(cart);
            }
        }
    }

    // Só dispara lembrete se o carrinho expirou com itens; carrinho
    // anônimo (sem cliente cadastrado) não tem e-mail pra mandar, só loga.
    private void sendAbandonedCartReminder(Cart cart) {
        customerPortOut.findCustomerById(cart.getCustomerId()).ifPresentOrElse(
                customer -> notificationPortOut.sendCartReminder(cart, customer.email(), customer.name()),
                () -> log.warn("Cliente {} não encontrado; lembrete de carrinho abandonado não enviado.", cart.getCustomerId())
        );
    }
}
