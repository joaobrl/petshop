package com.petshop.booking_service.infrastructure.messaging.adapter;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.ServiceDetails;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.infrastructure.messaging.dto.SendEmailCommandDto;
import com.petshop.commons.web.CorrelationIdFilter;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaAdapterOutTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private NotificationKafkaAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new NotificationKafkaAdapterOut(kafkaTemplate);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private Booking booking() {
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setPetId(UUID.randomUUID());
        booking.setOwnerName("Ciclana");
        booking.setOwnerContact("11999990000");
        booking.setOwnerEmail("ciclana@petshop.com");
        booking.setEmployeeName("Fulana");
        booking.setBookingDateTime(LocalDateTime.of(2026, 8, 15, 9, 0));
        booking.setServiceDetails(new ServiceDetails(ServiceType.BANHO, new BigDecimal("45.90"), 45, null, null));
        return booking;
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, Object> capturedRecord() {
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void sendBookingScheduledPublishesConfirmedTypeWithExpectedParams() {
        var booking = booking();

        adapter.sendBookingScheduled(booking);

        var record = capturedRecord();
        assertThat(record.topic()).isEqualTo(com.petshop.commons.messaging.KafkaTopics.NOTIFICATION_COMMANDS);
        assertThat(record.key()).isEqualTo(booking.getId().toString());
        var command = (SendEmailCommandDto) record.value();

        assertThat(command.to()).isEqualTo("ciclana@petshop.com");
        assertThat(command.tipo()).isEqualTo("AGENDAMENTO_CONFIRMADO");
        assertThat(command.params())
                .containsEntry("ownerName", "Ciclana")
                .containsEntry("petId", booking.getPetId().toString())
                .containsEntry("serviceType", "BANHO")
                .containsEntry("bookingDate", "15/08/2026 09:00")
                .containsEntry("price", "R$ 45,90");
    }

    @Test
    void sendBookingCompletedPublishesConcludedTypeWithExpectedParams() {
        var booking = booking();

        adapter.sendBookingCompleted(booking);

        var record = capturedRecord();
        assertThat(record.key()).isEqualTo(booking.getId().toString());
        var command = (SendEmailCommandDto) record.value();

        assertThat(command.tipo()).isEqualTo("AGENDAMENTO_CONCLUIDO");
        assertThat(command.params())
                .containsEntry("ownerName", "Ciclana")
                .containsEntry("petId", booking.getPetId().toString())
                .containsEntry("serviceType", "BANHO")
                .containsEntry("employeeName", "Fulana");
    }

    @Test
    void sendBookingCanceledPublishesCanceledTypeWithExpectedParams() {
        var booking = booking();

        adapter.sendBookingCanceled(booking);

        var record = capturedRecord();
        assertThat(record.key()).isEqualTo(booking.getId().toString());
        var command = (SendEmailCommandDto) record.value();

        assertThat(command.tipo()).isEqualTo("AGENDAMENTO_CANCELADO");
        assertThat(command.params())
                .containsEntry("ownerName", "Ciclana")
                .containsEntry("petId", booking.getPetId().toString())
                .containsEntry("serviceType", "BANHO");
    }

    @Test
    void includesCorrelationIdKafkaHeaderWhenPresentInMdc() {
        var booking = booking();
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-123");

        adapter.sendBookingScheduled(booking);

        var header = capturedRecord().headers().lastHeader(CorrelationIdFilter.HEADER);
        assertThat(header).isNotNull();
        assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo("corr-123");
    }

    @Test
    void omitsCorrelationIdHeaderWhenAbsentFromMdc() {
        var booking = booking();

        adapter.sendBookingScheduled(booking);

        assertThat(capturedRecord().headers().lastHeader(CorrelationIdFilter.HEADER)).isNull();
    }

    @Test
    void swallowsExceptionFromKafkaTemplate() {
        var booking = booking();
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenThrow(new RuntimeException("kafka indisponível"));

        assertThatCode(() -> adapter.sendBookingScheduled(booking)).doesNotThrowAnyException();
    }
}
