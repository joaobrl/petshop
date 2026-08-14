package com.petshop.order_service.infrastructure.messaging.adapter;

import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.infrastructure.messaging.dto.OrderCompletedEventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseHistoryKafkaAdapterOutTest {

    private static final String TOPIC = "order-completed";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private PurchaseHistoryKafkaAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new PurchaseHistoryKafkaAdapterOut(kafkaTemplate);
    }

    private Order sampleOrder() {
        var cart = Cart.openFor(UUID.randomUUID(), 24);
        cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);
        var order = Order.fromCart(cart);
        order.setId(UUID.randomUUID());
        order.markAsPaid();
        return order;
    }

    @Test
    void publishesOrderCompletedEventWithMappedItems() {
        var order = sampleOrder();

        adapter.publishOrderCompleted(order);

        verify(kafkaTemplate).send(eq(TOPIC), eq(order.getId().toString()), any(OrderCompletedEventDTO.class));
    }

    @Test
    void neverThrowsWhenKafkaTemplatePublishFails() {
        var order = sampleOrder();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenThrow(new RuntimeException("broker down"));

        adapter.publishOrderCompleted(order);
    }
}
