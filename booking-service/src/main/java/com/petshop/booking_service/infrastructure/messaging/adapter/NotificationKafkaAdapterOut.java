package com.petshop.booking_service.infrastructure.messaging.adapter;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.port.out.NotificationPortOut;
import com.petshop.booking_service.infrastructure.messaging.dto.SendEmailCommandDto;
import com.petshop.commons.messaging.KafkaTopics;
import com.petshop.commons.web.CorrelationIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaAdapterOut implements NotificationPortOut {

    // Precisam bater exatamente com os nomes das constantes de TipoNotificacao
    // no notification-service — ver tabela de mapeamento tipo → templateId lá.
    private static final String TIPO_AGENDAMENTO_CONFIRMADO = "AGENDAMENTO_CONFIRMADO";
    private static final String TIPO_AGENDAMENTO_CONCLUIDO = "AGENDAMENTO_CONCLUIDO";
    private static final String TIPO_AGENDAMENTO_CANCELADO = "AGENDAMENTO_CANCELADO";

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendBookingScheduled(Booking booking) {
        var params = Map.<String, Object>of(
                "ownerName", booking.getOwnerName(),
                "petId", booking.getPetId().toString(),
                "serviceType", booking.getServiceDetails().getServiceType().name(),
                "bookingDate", booking.getBookingDateTime().format(DATE_FORMATTER),
                "price", formatCurrency(booking.getServiceDetails().getPrice())
        );
        publishCommand(booking.getId().toString(), booking.getOwnerEmail(), TIPO_AGENDAMENTO_CONFIRMADO, params);
    }

    @Override
    public void sendBookingCompleted(Booking booking) {
        var params = Map.<String, Object>of(
                "ownerName", booking.getOwnerName(),
                "petId", booking.getPetId().toString(),
                "serviceType", booking.getServiceDetails().getServiceType().name(),
                "employeeName", booking.getEmployeeName()
        );
        publishCommand(booking.getId().toString(), booking.getOwnerEmail(), TIPO_AGENDAMENTO_CONCLUIDO, params);
    }

    @Override
    public void sendBookingCanceled(Booking booking) {
        var params = Map.<String, Object>of(
                "ownerName", booking.getOwnerName(),
                "petId", booking.getPetId().toString(),
                "serviceType", booking.getServiceDetails().getServiceType().name()
        );
        publishCommand(booking.getId().toString(), booking.getOwnerEmail(), TIPO_AGENDAMENTO_CANCELADO, params);
    }

    // NumberFormat.getCurrencyInstance(pt-BR) usa NBSP (não espaço comum)
    // entre "R$" e o valor — formata manualmente pra garantir "R$ 45,90"
    // com espaço normal, igual ao esperado no template do Brevo.
    private String formatCurrency(BigDecimal value) {
        return "R$ " + String.format(Locale.of("pt", "BR"), "%.2f", value);
    }

    private void publishCommand(String partitionKey, String to, String tipo, Map<String, Object> params) {
        var command = new SendEmailCommandDto(to, tipo, params);
        try {
            var record = new ProducerRecord<String, Object>(
                    KafkaTopics.NOTIFICATION_COMMANDS, null, partitionKey, command, correlationIdHeaders());
            kafkaTemplate.send(record);
            log.info("Successfully published email command '{}' to Topic [{}] for Booking ID: {}", tipo, KafkaTopics.NOTIFICATION_COMMANDS, partitionKey);
        } catch (Exception e) {
            log.error("Failed to publish email command '{}' to Topic [{}]", tipo, KafkaTopics.NOTIFICATION_COMMANDS, e);
        }
    }

    // O consumer (notification-service) extrai este header de volta pro MDC dele,
    // pra conseguir seguir o e-mail nos logs até a chamada real ao Brevo.
    private List<org.apache.kafka.common.header.Header> correlationIdHeaders() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            return List.of();
        }
        return List.of(new RecordHeader(CorrelationIdFilter.HEADER, correlationId.getBytes(StandardCharsets.UTF_8)));
    }
}
