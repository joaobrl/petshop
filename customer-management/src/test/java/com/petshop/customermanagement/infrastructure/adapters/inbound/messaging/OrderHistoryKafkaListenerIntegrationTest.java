package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging;

import com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto.OrderCompletedEventDTO;
import com.petshop.customermanagement.infrastructure.persistence.mongo.repository.CustomerPurchaseHistoryMongoRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
class OrderHistoryKafkaListenerIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.properties.security.protocol", () -> "PLAINTEXT");
        registry.add("spring.kafka.properties.sasl.mechanism", () -> "");
        registry.add("spring.kafka.properties.sasl.jaas.config", () -> "");
        registry.add("spring.kafka.producer.key-serializer",
                () -> "org.apache.kafka.common.serialization.StringSerializer");
        registry.add("spring.kafka.producer.value-serializer",
                () -> "org.springframework.kafka.support.serializer.JsonSerializer");
        registry.add("spring.kafka.consumer.auto-offset-reset", () -> "earliest");
    }

    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;

    @Autowired
    private CustomerPurchaseHistoryMongoRepository purchaseHistoryRepository;

    @Test
    void consumesOrderCompletedEventAndPersistsPurchaseHistory() {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var event = new OrderCompletedEventDTO(
                orderId,
                customerId,
                List.of(new OrderCompletedEventDTO.OrderItemEventDTO(1L, "Ração Premium", 2, 79.90)),
                159.80,
                LocalDateTime.now()
        );

        kafkaTemplate.send("order-completed", orderId.toString(), event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var saved = purchaseHistoryRepository.findByOrderId(orderId);
            assertThat(saved).isPresent();
            assertThat(saved.get().getCustomerId()).isEqualTo(customerId);
            assertThat(saved.get().getTotalAmount()).isEqualTo(159.80);
            assertThat(saved.get().getItems()).hasSize(1);
            assertThat(saved.get().getItems().get(0).getProductName()).isEqualTo("Ração Premium");
        });
    }

    @Test
    void consumerSurvivesPoisonPillAndProcessesNextValidMessage() throws Exception {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        try (var rawProducer = new KafkaProducer<String, byte[]>(producerProps)) {
            rawProducer.send(new ProducerRecord<>("order-completed", "poison-key",
                            "{isto não é json válido".getBytes(StandardCharsets.UTF_8)))
                    .get(10, TimeUnit.SECONDS);
        }

        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var event = new OrderCompletedEventDTO(
                orderId,
                customerId,
                List.of(new OrderCompletedEventDTO.OrderItemEventDTO(1L, "Ração Premium", 1, 50.0)),
                50.0,
                LocalDateTime.now()
        );

        kafkaTemplate.send("order-completed", orderId.toString(), event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var saved = purchaseHistoryRepository.findByOrderId(orderId);
            assertThat(saved).isPresent();
            assertThat(saved.get().getCustomerId()).isEqualTo(customerId);
        });
    }
}
