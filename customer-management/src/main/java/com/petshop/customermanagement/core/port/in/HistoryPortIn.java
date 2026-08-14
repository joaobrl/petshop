package com.petshop.customermanagement.core.port.in;

import com.petshop.customermanagement.core.domain.BookingHistory;
import com.petshop.customermanagement.core.domain.PurchaseHistory;

import java.util.List;
import java.util.UUID;

/**
 * Consulta de histórico (agendamentos e compras), gravado pelos listeners
 * Kafka (BookingHistoryKafkaListener/OrderHistoryKafkaListener). Ownership
 * (cliente só vê o próprio) é checado no controller, não aqui.
 */
public interface HistoryPortIn {

    List<BookingHistory> findCustomerBookingHistory(UUID customerId);

    List<PurchaseHistory> findCustomerPurchaseHistory(UUID customerId);

    /** Histórico de agendamentos de todos os clientes — visão da loja. */
    List<BookingHistory> findStoreBookingHistory();

    /** Histórico de compras de todos os clientes — visão da loja. */
    List<PurchaseHistory> findStorePurchaseHistory();
}
