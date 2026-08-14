package com.petshop.order_service.infrastructure.messaging.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCompletedEventDTOTest {

    @Test
    void exposesAllFieldsViaAccessors() {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var paidAt = LocalDateTime.now();
        var items = List.of(new OrderCompletedEventDTO.OrderItemEventDTO(1L, "Racao", 2, 10.0));

        var event = new OrderCompletedEventDTO(orderId, customerId, items, 20.0, paidAt);

        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.customerId()).isEqualTo(customerId);
        assertThat(event.items()).isEqualTo(items);
        assertThat(event.totalAmount()).isEqualTo(20.0);
        assertThat(event.paidAt()).isEqualTo(paidAt);
    }

    @Test
    void nestedItemEventExposesAllFieldsViaAccessors() {
        var item = new OrderCompletedEventDTO.OrderItemEventDTO(1L, "Racao", 2, 10.0);

        assertThat(item.productId()).isEqualTo(1L);
        assertThat(item.productName()).isEqualTo("Racao");
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.unitPrice()).isEqualTo(10.0);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var paidAt = LocalDateTime.now();
        var items = List.of(new OrderCompletedEventDTO.OrderItemEventDTO(1L, "Racao", 2, 10.0));

        var a = new OrderCompletedEventDTO(orderId, customerId, items, 20.0, paidAt);
        var b = new OrderCompletedEventDTO(orderId, customerId, items, 20.0, paidAt);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("orderId");
    }
}
