package com.petshop.notification_service.integration;

import com.petshop.commons.messaging.KafkaTopics;
import com.petshop.notification_service.core.domain.TipoNotificacao;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Utilitários compartilhados pelos testes de integração Kafka + Brevo real. */
final class BrevoRetryDltIntegrationSupport {

    static final String DLT = KafkaTopics.NOTIFICATION_COMMANDS + ".DLT";

    private BrevoRetryDltIntegrationSupport() {
    }

    static void sendCommand(EmbeddedKafkaBroker broker) {
        var producer = new KafkaTemplate<String, String>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
        producer.send(KafkaTopics.NOTIFICATION_COMMANDS,
                "{\"to\":\"cliente@petshop.com\",\"tipo\":\"" + TipoNotificacao.RESET_SENHA + "\",\"params\":{}}");
        producer.flush();
    }

    /** Offset atual do fim do DLT (partição 0), pra ignorar o que testes anteriores publicaram. */
    static long dltEndOffset(EmbeddedKafkaBroker broker) {
        var props = KafkaTestUtils.consumerProps(broker, "dlt-offset-" + UUID.randomUUID(), false);
        var factory = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new ByteArrayDeserializer());
        try (var consumer = factory.createConsumer()) {
            var partition = new TopicPartition(DLT, 0);
            return consumer.endOffsets(List.of(partition)).get(partition);
        }
    }

    /** Lê o DLT até chegar o primeiro registro (ou o timeout) e espera mais uma janela por extras. */
    static List<ConsumerRecord<String, byte[]>> readDlt(EmbeddedKafkaBroker broker, long fromOffset, Duration timeout) {
        var props = KafkaTestUtils.consumerProps(broker, "dlt-reader-" + UUID.randomUUID(), false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var factory = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new ByteArrayDeserializer());
        List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
        try (var consumer = factory.createConsumer()) {
            var partition = new TopicPartition(DLT, 0);
            consumer.assign(List.of(partition));
            consumer.seek(partition, fromOffset);
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline && records.isEmpty()) {
                consumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            consumer.poll(Duration.ofMillis(1000)).forEach(records::add);
        }
        return records;
    }
}
