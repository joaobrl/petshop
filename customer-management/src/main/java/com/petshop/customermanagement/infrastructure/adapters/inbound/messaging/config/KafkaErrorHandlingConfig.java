package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Sem isso, uma "poison pill" (ex.: evento de agendamento pra um CPF que não
 * existe) faria o {@link org.springframework.kafka.annotation.KafkaListener}
 * relançar a exceção indefinidamente, travando o consumo do tópico. Com esse
 * handler, a mensagem é reprocessada até 3 vezes (1s de intervalo) e, se
 * continuar falhando, vai pro tópico de dead-letter (sufixo ".DLT") em vez
 * de travar o consumer.
 */
@Slf4j
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaOperations);

        var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) ->
                log.warn("Falha ao processar mensagem do tópico [{}] (tentativa {}): {}",
                        record.topic(), deliveryAttempt, ex.getMessage()));

        return errorHandler;
    }
}
