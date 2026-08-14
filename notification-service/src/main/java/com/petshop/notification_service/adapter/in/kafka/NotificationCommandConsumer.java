package com.petshop.notification_service.adapter.in.kafka;

import com.petshop.commons.messaging.KafkaTopics;
import com.petshop.commons.web.CorrelationIdFilter;
import com.petshop.notification_service.adapter.in.kafka.dto.SendEmailCommandDto;
import com.petshop.notification_service.core.application.port.out.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationCommandConsumer {

    private final NotificationSender notificationSender;

    @KafkaListener(topics = KafkaTopics.NOTIFICATION_COMMANDS, groupId = "notification-group")
    public void consume(SendEmailCommandDto command,
                         @Header(value = CorrelationIdFilter.HEADER, required = false) byte[] correlationIdHeader) {
        String correlationId = correlationIdHeader != null
                ? new String(correlationIdHeader, StandardCharsets.UTF_8)
                : UUID.randomUUID().toString();
        MDC.put(CorrelationIdFilter.MDC_KEY, correlationId);
        try {
            log.info("Received command '{}' to send email to: {}", command.tipo(), command.to());
            notificationSender.enviar(command.tipo(), command.to(), command.params());
            log.info("Successfully processed email command '{}' for: {}", command.tipo(), command.to());
        } catch (Exception e) {
            log.error("Failed to process email command '{}' for: {}", command.tipo(), command.to(), e);
        } finally {
            MDC.remove(CorrelationIdFilter.MDC_KEY);
        }
    }
}
