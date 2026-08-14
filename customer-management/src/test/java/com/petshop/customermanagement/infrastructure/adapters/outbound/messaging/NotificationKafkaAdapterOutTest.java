package com.petshop.customermanagement.infrastructure.adapters.outbound.messaging;

import com.petshop.commons.messaging.KafkaTopics;
import com.petshop.commons.web.CorrelationIdFilter;
import com.petshop.customermanagement.infrastructure.adapters.outbound.messaging.dto.SendEmailCommandDto;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaAdapterOutTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private NotificationKafkaAdapterOut publisher;

    @BeforeEach
    void setUp() {
        publisher = new NotificationKafkaAdapterOut(kafkaTemplate);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, Object> capturedRecord() {
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void publishesSendPasswordResetCommandToNotificationCommandsTopic() {
        publisher.sendPasswordReset("maria@mail.com", "Maria", "N0vaSenha!");

        var record = capturedRecord();

        assertThat(record.topic()).isEqualTo(KafkaTopics.NOTIFICATION_COMMANDS);
        assertThat(record.value()).isEqualTo(
                new SendEmailCommandDto("maria@mail.com", "RESET_SENHA",
                        Map.of("name", "Maria", "newPassword", "N0vaSenha!")));
    }

    @Test
    void publishesSendRegistrationConfirmationCommandToNotificationCommandsTopic() {
        publisher.sendRegistrationConfirmation("maria@mail.com", "Maria");

        var record = capturedRecord();

        assertThat(record.topic()).isEqualTo(KafkaTopics.NOTIFICATION_COMMANDS);
        assertThat(record.value()).isEqualTo(
                new SendEmailCommandDto("maria@mail.com", "CADASTRO_CONFIRMADO", Map.of("name", "Maria")));
    }

    @Test
    void includesCorrelationIdKafkaHeaderWhenPresentInMdc() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-789");

        publisher.sendPasswordReset("maria@mail.com", "Maria", "N0vaSenha!");

        var header = capturedRecord().headers().lastHeader(CorrelationIdFilter.HEADER);
        assertThat(header).isNotNull();
        assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo("corr-789");
    }

    @Test
    void omitsCorrelationIdHeaderWhenAbsentFromMdc() {
        publisher.sendPasswordReset("maria@mail.com", "Maria", "N0vaSenha!");

        assertThat(capturedRecord().headers().lastHeader(CorrelationIdFilter.HEADER)).isNull();
    }
}
