package com.petshop.customermanagement.core.application.usecases;

import com.petshop.customermanagement.core.domain.PurchaseHistory;
import com.petshop.customermanagement.core.domain.PurchaseHistoryItem;
import com.petshop.customermanagement.core.port.in.dto.PurchaseCompletedCommand;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class UpdateCustomerPurchaseHistoryUseCase {

    private final PurchaseHistoryPortOut purchaseHistoryPortOut;

    public UpdateCustomerPurchaseHistoryUseCase(PurchaseHistoryPortOut purchaseHistoryPortOut) {
        this.purchaseHistoryPortOut = purchaseHistoryPortOut;
    }

    public void execute(PurchaseCompletedCommand command) {
        // Diferente do evento de agendamento, esse já vem com o customerId —
        // não precisa buscar o cliente por CPF.
        var history = purchaseHistoryPortOut.findByOrderId(command.orderId())
                .orElseGet(PurchaseHistory::new);

        history.setOrderId(command.orderId());
        history.setCustomerId(command.customerId());
        history.setTotalAmount(command.totalAmount());
        history.setPaidAt(command.paidAt());
        history.setItems(command.items().stream()
                .map(item -> new PurchaseHistoryItem(
                        item.productId(), item.productName(), item.quantity(), item.unitPrice()))
                .toList());

        purchaseHistoryPortOut.save(history);

        log.info("Histórico de compra atualizado para o pedido {} do cliente {}", command.orderId(), command.customerId());
    }
}
