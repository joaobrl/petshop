package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging;

import com.petshop.customermanagement.core.application.usecases.UpdateBookingHistoryUseCase;
import com.petshop.customermanagement.core.application.usecases.UpdateCustomerPurchaseHistoryUseCase;
import com.petshop.customermanagement.core.port.in.dto.BookingCompletedCommand;
import com.petshop.customermanagement.core.port.in.dto.PurchaseCompletedCommand;
import com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto.BookingEventDTO;
import com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto.OrderCompletedEventDTO;
import com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto.ServiceDetailsEventDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Os listeners Kafka são propositalmente "burros" — só repassam o DTO
 * desserializado para o use case correspondente. O teste garante essa
 * delegação, já que a integração real com um broker é coberta pelo teste
 * de integração com Testcontainers (OrderHistoryKafkaListenerIntegrationTest).
 */
@ExtendWith(MockitoExtension.class)
class KafkaListenersTest {

    @Nested
    class BookingHistoryKafkaListenerTest {

        @Mock
        private UpdateBookingHistoryUseCase useCase;

        private BookingHistoryKafkaListener listener;

        @BeforeEach
        void setUp() {
            listener = new BookingHistoryKafkaListener(useCase);
        }

        @Test
        void delegatesConsumedEventToUseCaseAsCommand() {
            var serviceDetails = new ServiceDetailsEventDTO("BANHO", null, null);
            var event = new BookingEventDTO(
                    UUID.randomUUID(), UUID.randomUUID(), "Maria", "12345678900", "11999999999",
                    serviceDetails, LocalDateTime.of(2026, 8, 10, 9, 0), "COMPLETED", null
            );

            listener.consumeBookingEvent(event);

            var captor = org.mockito.ArgumentCaptor.forClass(BookingCompletedCommand.class);
            verify(useCase).execute(captor.capture());
            var command = captor.getValue();
            assertThat(command.bookingId()).isEqualTo(event.id());
            assertThat(command.ownerCpf()).isEqualTo("12345678900");
            assertThat(command.serviceType()).isEqualTo("BANHO");
            assertThat(command.bookingDateTime()).isEqualTo(event.bookingDateTime());
            assertThat(command.status()).isEqualTo("COMPLETED");
        }
    }

    @Nested
    class OrderHistoryKafkaListenerTest {

        @Mock
        private UpdateCustomerPurchaseHistoryUseCase useCase;

        private OrderHistoryKafkaListener listener;

        @BeforeEach
        void setUp() {
            listener = new OrderHistoryKafkaListener(useCase);
        }

        @Test
        void delegatesConsumedEventToUseCaseAsCommand() {
            var orderId = UUID.randomUUID();
            var customerId = UUID.randomUUID();
            var paidAt = LocalDateTime.now();
            var event = new OrderCompletedEventDTO(orderId, customerId,
                    List.of(new OrderCompletedEventDTO.OrderItemEventDTO(1L, "Racao", 2, 50.0)), 100.0, paidAt);

            listener.consumeOrderCompletedEvent(event);

            var captor = org.mockito.ArgumentCaptor.forClass(PurchaseCompletedCommand.class);
            verify(useCase).execute(captor.capture());
            var command = captor.getValue();
            assertThat(command.orderId()).isEqualTo(orderId);
            assertThat(command.customerId()).isEqualTo(customerId);
            assertThat(command.totalAmount()).isEqualTo(100.0);
            assertThat(command.paidAt()).isEqualTo(paidAt);
            assertThat(command.items()).hasSize(1);
            assertThat(command.items().get(0).productName()).isEqualTo("Racao");
        }
    }
}
