package com.petshop.order_service.core.port.in;

import com.petshop.order_service.core.domain.CheckoutResult;
import com.petshop.order_service.core.domain.Order;

import java.util.List;
import java.util.UUID;

public interface OrderPortIn {

    /**
     * Finaliza só os itens selecionados do carrinho: reserva o estoque de
     * todos (tudo ou nada) e cria o pedido aguardando pagamento. Os itens
     * não selecionados continuam no carrinho.
     *
     * @param idempotencyKey opcional; repetir o checkout com a mesma chave
     *                       devolve o pedido já criado ({@code created=false})
     *                       em vez de reservar e criar outro.
     */
    CheckoutResult checkout(UUID customerId, List<Long> productIds, String idempotencyKey, String token);

    Order findById(UUID id);

    void processPendingPayments();

    /**
     * Chamado periodicamente pelo PickupReadyScheduler: avisa por e-mail
     * (e marca como READY_FOR_PICKUP) qualquer pedido pago cujo prazo de
     * preparo sorteado já venceu.
     */
    void processPickupReadyOrders();

    /**
     * Chamado periodicamente pelo OrderExpirationScheduler: cancela pedido
     * aguardando pagamento cuja reserva venceu e libera o estoque.
     */
    void expireUnpaidOrders();
}
