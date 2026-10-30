package com.petshop.order_service.core.port.in;

import com.petshop.order_service.core.domain.CartView;

import java.util.UUID;

/**
 * O carrinho é só uma lista de intenção: nada aqui reserva estoque (a
 * reserva acontece no checkout, ver OrderPortIn). As leituras devolvem a
 * disponibilidade atual de cada item ({@link CartView}).
 */
public interface CartPortIn {

    /**
     * Só aceita produto ativo e com estoque (404 / 422 "Produto sem
     * estoque"); a quantidade pedida não é comparada com o disponível aqui.
     */
    CartView addItem(UUID customerId, Long productId, int quantity, String token);

    CartView removeItem(UUID customerId, Long productId, String token);

    /**
     * Consulta todos os produtos do carrinho numa chamada só; produtos
     * desativados ou que não existem mais saem do carrinho (persistido).
     */
    CartView getCart(UUID customerId, String token);

    void expireOldCarts();
}
