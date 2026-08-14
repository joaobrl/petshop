package com.petshop.customermanagement.core.port.out;

import com.petshop.customermanagement.core.domain.PurchaseHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseHistoryPortOut {

    Optional<PurchaseHistory> findByOrderId(UUID orderId);

    List<PurchaseHistory> findAllByCustomerId(UUID customerId);

    /** Histórico de compras da loja inteira — visão ADMIN/RECEPTIONIST. */
    Page<PurchaseHistory> findAll(Pageable pageable);

    PurchaseHistory save(PurchaseHistory history);
}
