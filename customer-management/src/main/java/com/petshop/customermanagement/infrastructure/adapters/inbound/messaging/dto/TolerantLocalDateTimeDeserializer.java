package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Desserializador de transição (SDD-017) para datas dos eventos Kafka de
 * booking: aceita o formato novo ISO-8601 ({@code 2026-08-15T09:00:00}) e o
 * antigo brasileiro ({@code 15/08/2026 09:00}), para que mensagens já
 * enfileiradas antes do release não caiam no dead-letter-topic. Valores que
 * não são string (ex.: array {@code [2026,8,15,9,0]}) seguem o
 * desserializador padrão do jsr310.
 * <p>
 * Jackson 2 de propósito: o {@code JsonDeserializer} do spring-kafka usado
 * em {@code KafkaConsumerConfig} é baseado em {@code com.fasterxml.jackson}.
 */
public class TolerantLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final DateTimeFormatter LEGACY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return LocalDateTimeDeserializer.INSTANCE.deserialize(parser, context);
        }

        String value = parser.getText().trim();
        if (value.isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException isoFailure) {
            try {
                return LocalDateTime.parse(value, LEGACY_FORMAT);
            } catch (DateTimeParseException legacyFailure) {
                return (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, value,
                        "Esperado ISO-8601 (yyyy-MM-dd'T'HH:mm[:ss]) ou dd/MM/yyyy HH:mm");
            }
        }
    }
}
