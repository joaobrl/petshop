package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessagingDtoTest {

    @Test
    void bookingEventDtoIsAnImmutableRecordWithWorkingAccessors() {
        var id = UUID.randomUUID();
        var petId = UUID.randomUUID();
        var serviceDetails = new ServiceDetailsEventDTO("Banho e Tosa", BigDecimal.valueOf(80), 60);
        var bookingDateTime = LocalDateTime.of(2026, 8, 10, 14, 30);

        var event = new BookingEventDTO(
                id, petId, "Maria", "12345678900", "11999999999",
                serviceDetails, bookingDateTime, "CONFIRMED", "Nenhuma"
        );

        assertThat(event.id()).isEqualTo(id);
        assertThat(event.petId()).isEqualTo(petId);
        assertThat(event.ownerName()).isEqualTo("Maria");
        assertThat(event.ownerCpf()).isEqualTo("12345678900");
        assertThat(event.ownerContact()).isEqualTo("11999999999");
        assertThat(event.serviceType()).isEqualTo(serviceDetails);
        assertThat(event.bookingDateTime()).isEqualTo(bookingDateTime);
        assertThat(event.status()).isEqualTo("CONFIRMED");
        assertThat(event.observations()).isEqualTo("Nenhuma");
        assertThat(event.toString()).contains("Maria");
    }

    @Test
    void bookingEventDtoRecordEqualityIsFieldBased() {
        var id = UUID.randomUUID();
        var petId = UUID.randomUUID();
        var serviceDetails = new ServiceDetailsEventDTO("Banho e Tosa", BigDecimal.valueOf(80), 60);
        var bookingDateTime = LocalDateTime.of(2026, 8, 10, 14, 30);

        var event1 = new BookingEventDTO(
                id, petId, "Maria", "12345678900", "11999999999",
                serviceDetails, bookingDateTime, "CONFIRMED", "Nenhuma"
        );
        var event2 = new BookingEventDTO(
                id, petId, "Maria", "12345678900", "11999999999",
                serviceDetails, bookingDateTime, "CONFIRMED", "Nenhuma"
        );

        assertThat(event1).isEqualTo(event2).hasSameHashCodeAs(event2);
    }

    @Test
    void serviceDetailsEventDtoIsAnImmutableRecordWithWorkingAccessors() {
        var details = new ServiceDetailsEventDTO("Banho e Tosa", BigDecimal.valueOf(80), 60);

        assertThat(details.serviceType()).isEqualTo("Banho e Tosa");
        assertThat(details.price()).isEqualTo(BigDecimal.valueOf(80));
        assertThat(details.durationInMinutes()).isEqualTo(60);
    }

    @Test
    void orderCompletedEventDtoIsAnImmutableRecordWithWorkingAccessors() {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var paidAt = LocalDateTime.now();
        var items = List.of(new OrderCompletedEventDTO.OrderItemEventDTO(1L, "Racao", 2, 50.0));

        var event = new OrderCompletedEventDTO(orderId, customerId, items, 100.0, paidAt);

        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.customerId()).isEqualTo(customerId);
        assertThat(event.items()).isEqualTo(items);
        assertThat(event.totalAmount()).isEqualTo(100.0);
        assertThat(event.paidAt()).isEqualTo(paidAt);

        var item = event.items().get(0);
        assertThat(item.productId()).isEqualTo(1L);
        assertThat(item.productName()).isEqualTo("Racao");
        assertThat(item.quantity()).isEqualTo(2);
        assertThat(item.unitPrice()).isEqualTo(50.0);
    }

    @Test
    void orderCompletedEventDtoRecordEqualityIsFieldBased() {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var paidAt = LocalDateTime.now();
        var items = List.of(new OrderCompletedEventDTO.OrderItemEventDTO(1L, "Racao", 2, 50.0));

        var event1 = new OrderCompletedEventDTO(orderId, customerId, items, 100.0, paidAt);
        var event2 = new OrderCompletedEventDTO(orderId, customerId, items, 100.0, paidAt);

        assertThat(event1).isEqualTo(event2);
        assertThat(event1.hashCode()).isEqualTo(event2.hashCode());
    }
}
