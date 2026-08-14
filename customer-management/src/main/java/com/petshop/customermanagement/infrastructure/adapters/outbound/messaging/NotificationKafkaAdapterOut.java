package com.petshop.customermanagement.infrastructure.adapters.outbound.messaging;

import com.petshop.commons.messaging.KafkaTopics;
import com.petshop.commons.web.CorrelationIdFilter;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.customermanagement.infrastructure.adapters.outbound.messaging.dto.SendEmailCommandDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Publica no mesmo tópico "notification-commands" que o
 * notification-service já escuta (usado hoje pra emails transacionais de
 * booking/order) — reaproveitado aqui pro email de "esqueci minha senha".
 * Nenhuma mudança necessária no notification-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaAdapterOut implements NotificationPortOut {

    // Precisam bater exatamente com o nome das constantes de TipoNotificacao
    // no notification-service.
    private static final String TIPO_RESET_SENHA = "RESET_SENHA";
    private static final String TIPO_CADASTRO_CONFIRMADO = "CADASTRO_CONFIRMADO";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendPasswordReset(String to, String name, String newPassword) {
        var params = Map.<String, Object>of("name", name, "newPassword", newPassword);
        var command = new SendEmailCommandDto(to, TIPO_RESET_SENHA, params);
        var record = new ProducerRecord<String, Object>(
                KafkaTopics.NOTIFICATION_COMMANDS, null, null, command, correlationIdHeaders());
        kafkaTemplate.send(record);
        log.info("Published password-reset email command for: {}", to);
    }

    @Override
    public void sendRegistrationConfirmation(String to, String name) {
        var params = Map.<String, Object>of("name", name);
        var command = new SendEmailCommandDto(to, TIPO_CADASTRO_CONFIRMADO, params);
        var record = new ProducerRecord<String, Object>(
                KafkaTopics.NOTIFICATION_COMMANDS, null, null, command, correlationIdHeaders());
        kafkaTemplate.send(record);
        log.info("Published registration-confirmation email command for: {}", to);
    }

    // O consumer (notification-service) extrai este header de volta pro MDC dele,
    // pra conseguir seguir o e-mail nos logs até a chamada real ao Brevo.
    private List<Header> correlationIdHeaders() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            return List.of();
        }
        return List.of(new RecordHeader(CorrelationIdFilter.HEADER, correlationId.getBytes(StandardCharsets.UTF_8)));
    }
}
