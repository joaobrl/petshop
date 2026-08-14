package com.petshop.order_service.core.port.out;

import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.Order;

/**
 * Notificações por e-mail do fluxo de compra:
 * 1. pedido criado / aguardando pagamento
 * 2. pagamento confirmado + aviso de retirada (prazo estimado)
 * 3. nota fiscal emitida (simulada, só o aviso por e-mail)
 * 4. pedido pronto pra retirada (quando o prazo estimado vence)
 * 5. lembrete de carrinho abandonado (24h sem finalizar a compra)
 */
public interface NotificationPortOut {
    void sendOrderAwaitingPayment(Order order, String customerEmail, String customerName);

    void sendPaymentConfirmed(Order order, String customerEmail, String customerName);

    void sendInvoiceIssued(Order order, String customerEmail, String customerName);

    /**
     * Disparado assim que o pagamento é confirmado: avisa que o pedido
     * está sendo preparado e que a retirada pode levar até 1h (mensagem
     * fixa pro cliente — o prazo real, sorteado só pra fins de teste, fica
     * só no log do order-service).
     */
    void sendPickupWindow(Order order, String customerEmail, String customerName);

    /**
     * Disparado quando o prazo de preparo (sorteado, ver OrderService)
     * vence — avisa que já pode vir buscar.
     */
    void sendOrderReadyForPickup(Order order, String customerEmail, String customerName);

    /**
     * Disparado quando um carrinho expira (24h) ainda com itens dentro —
     * lembrete/propaganda pra retomar a compra.
     */
    void sendCartReminder(Cart cart, String customerEmail, String customerName);
}
