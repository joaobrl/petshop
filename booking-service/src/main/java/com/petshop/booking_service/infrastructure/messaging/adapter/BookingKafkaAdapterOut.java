package com.petshop.booking_service.infrastructure.messaging.adapter;

import com.petshop.booking_service.core.port.out.BookingHistoryPortOut;
import com.petshop.booking_service.core.port.out.dto.BookingResponseDto;
import com.petshop.commons.messaging.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingKafkaAdapterOut implements BookingHistoryPortOut {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishBookingScheduled(BookingResponseDto bookingEvent) {
        publishEvent(KafkaTopics.BOOKING_SCHEDULED, bookingEvent);
    }

    @Override
    public void publishBookingCanceled(BookingResponseDto bookingEvent) {
        publishEvent(KafkaTopics.BOOKING_CANCELED, bookingEvent);
    }

    @Override
    public void publishBookingCompleted(BookingResponseDto bookingEvent) {
        publishEvent(KafkaTopics.BOOKING_COMPLETED, bookingEvent);
    }

    private void publishEvent(String topic, BookingResponseDto event) {
        String partitionKey = event.getId().toString();
        try {
            kafkaTemplate.send(topic, partitionKey, event);
            log.info("Successfully published history event to Topic [{}] for Booking ID: {}", topic, partitionKey);
        } catch (Exception e) {
            log.error("Failed to publish history event to Topic [{}] for Booking ID: {}", topic, partitionKey, e);
        }
    }
}
