package com.petshop.notification_service.integration;

import com.petshop.notification_service.core.application.port.out.NotificationSender;
import com.petshop.notification_service.core.domain.TipoNotificacao;
import feign.RetryableException;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CA8 do SDD-004: Brevo inalcançável (porta fechada) gera
 * {@link RetryableException} (falha de I/O, status -1), que não está entre as
 * não retentáveis — logo é reprocessada com backoff e só então vai ao DLT.
 */
@SpringBootTest(properties = {
        "spring.kafka.properties.security.protocol=PLAINTEXT",
        "spring.kafka.properties.sasl.mechanism=",
        "spring.kafka.properties.sasl.jaas.config=",
        "notification.kafka.retry.initial-interval-ms=50",
        "brevo.api-key=chave-de-teste",
        "brevo.templates.RESET_SENHA=7"
})
@EmbeddedKafka(partitions = 1, topics = {"notification-commands", "notification-commands.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class BrevoConnectionRefusedRetryDltIntegrationTest {

    @DynamicPropertySource
    static void closedPort(DynamicPropertyRegistry registry) throws IOException {
        int port;
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        } // fechado ao sair do try: ninguém escuta nessa porta.
        registry.add("brevo.base-url", () -> "http://127.0.0.1:" + port);
    }

    @Autowired
    private NotificationSender sender;
    @Autowired
    private EmbeddedKafkaBroker broker;
    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void connectionRefusedIsARetryableException() {
        assertThatThrownBy(() -> sender.enviar(TipoNotificacao.RESET_SENHA, "a@b.com", Map.of()))
                .isInstanceOf(RetryableException.class);
    }

    @Test
    void ca8_connectionRefusedIsRetriedAndThenGoesToTheDlt() {
        double before = meterRegistry.counter("notification.send.failed", "reason", "connection_error").count();

        long dltStart = BrevoRetryDltIntegrationSupport.dltEndOffset(broker);
        BrevoRetryDltIntegrationSupport.sendCommand(broker);

        var dlt = BrevoRetryDltIntegrationSupport.readDlt(broker, dltStart, Duration.ofSeconds(20));
        assertThat(dlt).hasSize(1);
        assertThat(new String(dlt.get(0).headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN).value(),
                StandardCharsets.UTF_8)).isEqualTo(RetryableException.class.getName());
        // 4 tentativas do listener (1 + 3 retries), cada uma uma falha de conexão.
        assertThat(meterRegistry.counter("notification.send.failed", "reason", "connection_error").count() - before)
                .isEqualTo(4.0);
    }
}
