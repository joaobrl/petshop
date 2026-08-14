package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging;

import com.petshop.commons.messaging.KafkaTopics;
import com.petshop.customermanagement.core.application.usecases.UpdateCustomerPurchaseHistoryUseCase;
import com.petshop.customermanagement.core.port.in.dto.PurchaseCompletedCommand;
import com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto.OrderCompletedEventDTO;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderHistoryKafkaListener {

    private final UpdateCustomerPurchaseHistoryUseCase useCase;

    public OrderHistoryKafkaListener(UpdateCustomerPurchaseHistoryUseCase useCase) {
        this.useCase = useCase;
    }

    @KafkaListener(topics = KafkaTopics.ORDER_COMPLETED, groupId = "customer-management-group",
            containerFactory = "orderCompletedKafkaListenerContainerFactory")
    public void consumeOrderCompletedEvent(OrderCompletedEventDTO event) {
        // Traduz o DTO de mensageria pro command do core aqui — o use case
        // não deve conhecer o formato do evento Kafka.
        var items = event.items().stream()
                .map(item -> new PurchaseCompletedCommand.PurchaseItemCommand(
                        item.productId(), item.productName(), item.quantity(), item.unitPrice()))
                .toList();
        useCase.execute(new PurchaseCompletedCommand(
                event.orderId(), event.customerId(), items, event.totalAmount(), event.paidAt()));
    }
}
