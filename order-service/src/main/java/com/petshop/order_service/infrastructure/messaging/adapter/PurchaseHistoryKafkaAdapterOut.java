package com.petshop.order_service.infrastructure.messaging.adapter;

import com.petshop.commons.messaging.KafkaTopics;
import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.core.port.out.PurchaseHistoryPortOut;
import com.petshop.order_service.infrastructure.messaging.dto.OrderCompletedEventDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PurchaseHistoryKafkaAdapterOut implements PurchaseHistoryPortOut {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishOrderCompleted(Order order) {
        var items = order.getItems().stream()
                .map(item -> new OrderCompletedEventDTO.OrderItemEventDTO(
                        item.getProductId(), item.getProductName(), item.getQuantity(), item.getUnitPrice()))
                .toList();

        var event = new OrderCompletedEventDTO(
                order.getId(), order.getCustomerId(), items, order.getTotalAmount(), order.getPaidAt());

        try {
            kafkaTemplate.send(KafkaTopics.ORDER_COMPLETED, order.getId().toString(), event);
            log.info("Evento order-completed publicado para o pedido {}", order.getId());
        } catch (Exception e) {
            log.error("Falha ao publicar evento order-completed pro pedido {}", order.getId(), e);
        }
    }
}
