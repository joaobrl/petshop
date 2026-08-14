package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DefaultErrorHandler;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O comportamento de retry/dead-letter em si é infraestrutura do Spring
 * Kafka (DefaultErrorHandler/DeadLetterPublishingRecoverer), exercitada de
 * ponta a ponta pelo teste de integração com Testcontainers
 * (OrderHistoryKafkaListenerIntegrationTest). Aqui garantimos que o bean é
 * construído corretamente, sem lançar exceção, a partir de um
 * KafkaOperations qualquer.
 */
class KafkaErrorHandlingConfigTest {

    private final KafkaErrorHandlingConfig config = new KafkaErrorHandlingConfig();

    @SuppressWarnings("unchecked")
    @Test
    void kafkaErrorHandlerBeanIsCreatedSuccessfully() {
        KafkaOperations<Object, Object> kafkaOperations = Mockito.mock(KafkaOperations.class);

        DefaultErrorHandler handler = config.kafkaErrorHandler(kafkaOperations);

        assertThat(handler).isNotNull().isInstanceOf(DefaultErrorHandler.class);
    }
}
