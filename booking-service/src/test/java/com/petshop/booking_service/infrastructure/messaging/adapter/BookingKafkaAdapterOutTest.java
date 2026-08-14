package com.petshop.booking_service.infrastructure.messaging.adapter;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.ServiceDetails;
import com.petshop.booking_service.core.port.out.dto.BookingResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingKafkaAdapterOutTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private BookingKafkaAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new BookingKafkaAdapterOut(kafkaTemplate);
    }

    private BookingResponseDto event() {
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setServiceDetails(new ServiceDetails());
        return new BookingResponseDto(booking);
    }

    @Test
    void publishBookingScheduledSendsToScheduledTopic() {
        var event = event();

        adapter.publishBookingScheduled(event);

        verify(kafkaTemplate).send(eq("booking-scheduled"), eq(event.getId().toString()), eq(event));
    }

    @Test
    void publishBookingCanceledSendsToCanceledTopic() {
        var event = event();

        adapter.publishBookingCanceled(event);

        verify(kafkaTemplate).send(eq("booking-canceled"), eq(event.getId().toString()), eq(event));
    }

    @Test
    void publishBookingCompletedSendsToCompletedTopic() {
        var event = event();

        adapter.publishBookingCompleted(event);

        verify(kafkaTemplate).send(eq("booking-completed"), eq(event.getId().toString()), eq(event));
    }

    @Test
    void swallowsExceptionFromKafkaTemplate() {
        var event = event();
        when(kafkaTemplate.send(any(String.class), any(String.class), any()))
                .thenThrow(new RuntimeException("kafka indisponível"));

        assertThatCode(() -> adapter.publishBookingScheduled(event)).doesNotThrowAnyException();
    }
}
