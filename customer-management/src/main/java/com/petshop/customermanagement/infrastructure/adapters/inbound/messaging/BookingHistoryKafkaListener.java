package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging;

import com.petshop.commons.messaging.KafkaTopics;
import com.petshop.customermanagement.core.application.usecases.UpdateBookingHistoryUseCase;
import com.petshop.customermanagement.core.port.in.dto.BookingCompletedCommand;
import com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto.BookingEventDTO;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class BookingHistoryKafkaListener {

    private final UpdateBookingHistoryUseCase useCase;

    public BookingHistoryKafkaListener(UpdateBookingHistoryUseCase useCase) {
        this.useCase = useCase;
    }

    @KafkaListener(topics = KafkaTopics.BOOKING_COMPLETED, groupId = "customer-management-group",
            containerFactory = "bookingCompletedKafkaListenerContainerFactory")
    public void consumeBookingEvent(BookingEventDTO event) {
        // Traduz o DTO de mensageria pro command do core aqui — o use case
        // não deve conhecer o formato do evento Kafka.
        useCase.execute(new BookingCompletedCommand(
                event.id(),
                event.ownerCpf(),
                event.serviceType().serviceType(),
                event.bookingDateTime(),
                event.status()
        ));
    }
}