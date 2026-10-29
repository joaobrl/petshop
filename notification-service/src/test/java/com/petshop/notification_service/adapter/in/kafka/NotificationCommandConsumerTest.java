package com.petshop.notification_service.adapter.in.kafka;

import com.petshop.commons.web.CorrelationIdFilter;
import com.petshop.notification_service.adapter.in.kafka.dto.SendEmailCommandDto;
import com.petshop.notification_service.core.application.port.out.NotificationSender;
import com.petshop.notification_service.core.domain.TipoNotificacao;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationCommandConsumerTest {

    @Mock
    private NotificationSender notificationSender;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private NotificationCommandConsumer consumer;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void delegatesTheCommandDirectlyToTheNotificationSender() {
        consumer = new NotificationCommandConsumer(notificationSender, meterRegistry);
        var params = Map.<String, Object>of("ownerName", "Ciclana");
        var command = new SendEmailCommandDto("cliente@petshop.com", TipoNotificacao.AGENDAMENTO_CONFIRMADO, params);

        consumer.consume(command, null);

        verify(notificationSender).enviar(TipoNotificacao.AGENDAMENTO_CONFIRMADO, "cliente@petshop.com", params);
    }

    @Test
    void rethrowsExceptionsSoTheErrorHandlerCanRetryOrSendToTheDlt() {
        consumer = new NotificationCommandConsumer(notificationSender, meterRegistry);
        var command = new SendEmailCommandDto("cliente@petshop.com", TipoNotificacao.AGENDAMENTO_CONFIRMADO, Map.of());
        doThrow(new RuntimeException("Brevo indisponível"))
                .when(notificationSender).enviar(any(), any(), any());

        assertThatThrownBy(() -> consumer.consume(command, null))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Brevo indisponível");
        assertThat(meterRegistry.counter("notification.send.failed", "reason", "unexpected").count()).isEqualTo(1.0);
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void countsFeignFailuresByReason() {
        consumer = new NotificationCommandConsumer(notificationSender, meterRegistry);
        var command = new SendEmailCommandDto("cliente@petshop.com", TipoNotificacao.AGENDAMENTO_CONFIRMADO, Map.of());
        var request = Request.create(Request.HttpMethod.POST, "http://brevo/smtp/email", Map.of(),
                new byte[0], StandardCharsets.UTF_8, new RequestTemplate());
        doThrow(FeignException.errorStatus("enviarEmail",
                feign.Response.builder().status(503).reason("x").request(request).headers(Map.of()).build()))
                .doThrow(new RetryableException(-1, "timeout", Request.HttpMethod.POST, (Long) null, request))
                .when(notificationSender).enviar(any(), any(), any());

        assertThatThrownBy(() -> consumer.consume(command, null)).isInstanceOf(FeignException.class);
        assertThatThrownBy(() -> consumer.consume(command, null)).isInstanceOf(FeignException.class);

        assertThat(meterRegistry.counter("notification.send.failed", "reason", "http_5xx").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("notification.send.failed", "reason", "connection_error").count()).isEqualTo(1.0);
    }

    @Test
    void putsCorrelationIdFromKafkaHeaderInMdcDuringProcessingAndClearsItAfter() {
        consumer = new NotificationCommandConsumer(notificationSender, meterRegistry);
        var command = new SendEmailCommandDto("cliente@petshop.com", TipoNotificacao.AGENDAMENTO_CONFIRMADO, Map.of());
        byte[] header = "corr-from-header".getBytes(StandardCharsets.UTF_8);
        doAnswer(invocation -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isEqualTo("corr-from-header");
            return null;
        }).when(notificationSender).enviar(any(), any(), any());

        consumer.consume(command, header);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesANewCorrelationIdWhenTheKafkaHeaderIsAbsent() {
        consumer = new NotificationCommandConsumer(notificationSender, meterRegistry);
        var command = new SendEmailCommandDto("cliente@petshop.com", TipoNotificacao.AGENDAMENTO_CONFIRMADO, Map.of());
        doAnswer(invocation -> {
            assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNotBlank();
            return null;
        }).when(notificationSender).enviar(any(), any(), any());

        consumer.consume(command, null);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
