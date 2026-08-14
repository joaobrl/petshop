package com.petshop.order_service.core.port.out;

import com.petshop.order_service.core.domain.Order;

/**
 * Publica o evento de compra concluída no Kafka, pro customer-management
 * consumir e gravar o histórico de compras no Mongo (mesmo padrão usado
 * pro histórico de agendamento).
 */
public interface PurchaseHistoryPortOut {
    void publishOrderCompleted(Order order);
}
