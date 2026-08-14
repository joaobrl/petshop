package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging;

import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto.BookingEventDTO;
import com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto.ServiceDetailsEventDTO;
import com.petshop.customermanagement.infrastructure.persistence.mongo.repository.CustomerHistoryBookingsMongoRepository;
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
class BookingHistoryKafkaListenerIntegrationTest {

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
    private CustomerHistoryBookingsMongoRepository bookingHistoryRepository;

    @Autowired
    private CustomerPortOut customerPortOut;

    private BookingEventDTO event(UUID bookingId, String ownerCpf) {
        return new BookingEventDTO(
                bookingId,
                UUID.randomUUID(),
                "Ciclana",
                ownerCpf,
                "11999990000",
                new ServiceDetailsEventDTO("BANHO", null, null),
                LocalDateTime.of(2026, 8, 15, 9, 0),
                "COMPLETED",
                null
        );
    }

    private Customer customerWithPet(String cpf) {
        var pet = new Pet();
        pet.setId(UUID.randomUUID());
        pet.setPetName("Rex");
        pet.setPetType(PetType.DOG);
        var customer = new Customer("Ciclana", cpf, cpf + "@petshop.com", "11999990000");
        customer.setPet(List.of(pet));
        return customerPortOut.save(customer);
    }

    @Test
    void consumesBookingCompletedEventAndPersistsBookingHistory() {
        var bookingId = UUID.randomUUID();
        var ownerCpf = "12345678900";
        var customer = customerWithPet(ownerCpf);

        kafkaTemplate.send("booking-completed", bookingId.toString(), event(bookingId, ownerCpf));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var saved = bookingHistoryRepository.findByBookingId(bookingId);
            assertThat(saved).isPresent();
            assertThat(saved.get().getCustomerId()).isEqualTo(customer.getId());
            assertThat(saved.get().getServiceType()).isEqualTo("BANHO");
            assertThat(saved.get().getStatus()).isEqualTo("COMPLETED");
        });
    }

    @Test
    void consumerSurvivesPoisonPillAndProcessesNextValidBookingEvent() throws Exception {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        try (var rawProducer = new KafkaProducer<String, byte[]>(producerProps)) {
            rawProducer.send(new ProducerRecord<>("booking-completed", "poison-key",
                            "{isto não é json válido".getBytes(StandardCharsets.UTF_8)))
                    .get(10, TimeUnit.SECONDS);
        }

        var bookingId = UUID.randomUUID();
        var ownerCpf = "98765432100";
        customerWithPet(ownerCpf);

        kafkaTemplate.send("booking-completed", bookingId.toString(), event(bookingId, ownerCpf));

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            var saved = bookingHistoryRepository.findByBookingId(bookingId);
            assertThat(saved).isPresent();
        });
    }
}
