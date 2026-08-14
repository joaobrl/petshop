package com.petshop.order_service.core.port.in;

import com.petshop.order_service.core.domain.Cart;

import java.util.UUID;

public interface CartPortIn {

    /**
     * @param reserveStock false quando quem está adicionando não está
     *                      logado — o item entra no carrinho, mas o
     *                      estoque não é reservado.
     */
    Cart addItem(UUID customerId, Long productId, int quantity, boolean reserveStock);

    Cart removeItem(UUID customerId, Long productId);

    Cart getCart(UUID customerId);

    void expireOldCarts();
}
