package com.petshop.notification_service.adapter.in.kafka.config;

import com.petshop.notification_service.adapter.in.kafka.dto.SendEmailCommandDto;
import feign.FeignException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

/**
 * Sem isso, uma falha no envio (Brevo fora do ar, timeout) ou uma "poison
 * pill" (JSON inválido) não tinha destino: o e-mail — inclusive recuperação
 * de senha — se perdia em silêncio. Com esse handler, falha transitória é
 * reprocessada com backoff exponencial (1s, 2s, 4s) e, esgotadas as
 * tentativas, a mensagem vai pro tópico de dead-letter (sufixo ".DLT") com
 * os headers padrão do Spring Kafka. Falha não retentável (4xx do Brevo,
 * desserialização) vai direto pro DLT, sem retry.
 * <p>
 * O {@code KafkaTemplate} usado pra publicar no DLT é montado aqui (e não é
 * um bean) porque o serviço não tem producer configurado: precisa serializar
 * tanto o {@code SendEmailCommandDto} já desserializado quanto os bytes
 * originais de uma mensagem que falhou na desserialização.
 */
@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    static final String DLT_SUFFIX = ".DLT";

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaProperties kafkaProperties,
            MeterRegistry meterRegistry,
            @Value("${notification.kafka.retry.max-attempts:3}") int maxAttempts,
            @Value("${notification.kafka.retry.initial-interval-ms:1000}") long initialIntervalMs,
            @Value("${notification.kafka.retry.multiplier:2.0}") double multiplier) {
        var recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaOperations(kafkaProperties),
                // partição -1: o DLT é auto-criado (pode ter menos partições
                // que o tópico original), então deixa o producer escolher.
                (record, ex) -> new TopicPartition(record.topic() + DLT_SUFFIX, -1));

        var backOff = new ExponentialBackOff(initialIntervalMs, multiplier);
        backOff.setMaxAttempts(maxAttempts);

        var errorHandler = new DefaultErrorHandler((record, ex) -> {
            recoverer.accept(record, ex);
            meterRegistry.counter("notification.dlt.published", "topic", record.topic()).increment();
            log.error("Mensagem do tópico [{}] (partição {}, offset {}) enviada para o DLT: {}",
                    record.topic(), record.partition(), record.offset(), ex.getMessage());
        }, backOff);

        // 4xx do Brevo: repetir a mesma requisição não muda o resultado.
        // (408/429 ficam de fora de propósito — são transitórios.)
        errorHandler.addNotRetryableExceptions(
                FeignException.BadRequest.class,
                FeignException.Unauthorized.class,
                FeignException.Forbidden.class,
                FeignException.NotFound.class,
                FeignException.UnprocessableEntity.class,
                DeserializationException.class);

        // Sem o corpo do e-mail: só tópico, tentativa e a mensagem da exceção.
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Falha ao processar mensagem do tópico [{}] (tentativa {}): {}",
                        record.topic(), deliveryAttempt, ex.getMessage()));

        return errorHandler;
    }

    private KafkaOperations<Object, Object> dltKafkaOperations(KafkaProperties kafkaProperties) {
        Map<Class<?>, Serializer<?>> keySerializers = Map.of(
                byte[].class, new ByteArraySerializer(),
                String.class, new StringSerializer());
        Map<Class<?>, Serializer<?>> valueSerializers = Map.of(
                byte[].class, new ByteArraySerializer(),
                SendEmailCommandDto.class, new JacksonJsonSerializer<SendEmailCommandDto>());

        var producerFactory = new DefaultKafkaProducerFactory<Object, Object>(
                kafkaProperties.buildProducerProperties(),
                new DelegatingByTypeSerializer(keySerializers, true),
                new DelegatingByTypeSerializer(valueSerializers, true));
        return new KafkaTemplate<>(producerFactory);
    }
}
