package com.petshop.notification_service.adapter.in.kafka.config;

import com.petshop.notification_service.adapter.in.kafka.NotificationCommandConsumer;
import com.petshop.notification_service.adapter.in.kafka.dto.SendEmailCommandDto;
import com.petshop.notification_service.core.domain.TipoNotificacao;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Exercita o {@link DefaultErrorHandler} de ponta a ponta contra um broker
 * Kafka embarcado (sem Docker), com o backoff encurtado pra não alongar a
 * suíte: o consumer real ({@link NotificationCommandConsumer}) roda num
 * container de listener, o {@code NotificationSender} é um stub que falha
 * conforme o cenário, e o DLT é lido de volta do broker.
 */
@EmbeddedKafka(partitions = 1)
class KafkaErrorHandlingConfigTest {

    private static final Duration SETTLE = Duration.ofSeconds(2);

    private EmbeddedKafkaBroker broker;
    private SimpleMeterRegistry meterRegistry;
    private KafkaMessageListenerContainer<String, SendEmailCommandDto> container;
    private KafkaTemplate<String, String> producer;
    private String topic;
    private final AtomicInteger attempts = new AtomicInteger();
    private final List<String> sent = new ArrayList<>();

    @BeforeEach
    void setUp() {
        broker = org.springframework.kafka.test.condition.EmbeddedKafkaCondition.getBroker();
        meterRegistry = new SimpleMeterRegistry();
        topic = "notification-commands-" + UUID.randomUUID();
        broker.addTopics(topic, topic + ".DLT");
        producer = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
    }

    @AfterEach
    void tearDown() {
        if (container != null) {
            container.stop();
        }
    }

    @Test
    void transientFailureIsRetriedAndThenSucceedsWithoutGoingToTheDlt() {
        startListener(to -> {
            if (attempts.incrementAndGet() <= 2) {
                throw brevoError(503);
            }
            sent.add(to);
        });

        producer.send(topic, validPayload());

        await().atMost(Duration.ofSeconds(15)).until(() -> sent.size() == 1);
        assertThat(attempts).hasValue(3);
        assertThat(readDlt(SETTLE)).isEmpty();
        assertThat(meterRegistry.counter("notification.send.failed", "reason", "http_5xx").count()).isEqualTo(2.0);
    }

    @Test
    void persistentFailureGoesToTheDltAfterAllAttemptsWithTheExceptionHeaders() {
        startListener(to -> {
            attempts.incrementAndGet();
            throw brevoError(503);
        });

        producer.send(topic, validPayload());

        List<ConsumerRecord<String, byte[]>> dlt = readDlt(Duration.ofSeconds(15));
        assertThat(dlt).hasSize(1);
        assertThat(attempts).hasValue(4);
        assertThat(header(dlt.get(0), KafkaHeaders.DLT_EXCEPTION_MESSAGE)).isNotBlank();
        assertThat(header(dlt.get(0), KafkaHeaders.DLT_EXCEPTION_FQCN)).isNotBlank();
        assertThat(header(dlt.get(0), KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(topic);
        // Formato do payload publicado no DLT (RNF3): JSON do DTO + header de tipo.
        assertThat(new String(dlt.get(0).value(), StandardCharsets.UTF_8))
                .isEqualTo("{\"to\":\"cliente@petshop.com\",\"tipo\":\"RESET_SENHA\",\"params\":{}}");
        assertThat(header(dlt.get(0), "__TypeId__")).isEqualTo(SendEmailCommandDto.class.getName());
        assertThat(meterRegistry.counter("notification.dlt.published", "topic", topic).count()).isEqualTo(1.0);
    }

    @Test
    void nonRetryableFailureGoesStraightToTheDltAfterASingleAttempt() {
        startListener(to -> {
            attempts.incrementAndGet();
            throw brevoError(400);
        });

        producer.send(topic, validPayload());

        List<ConsumerRecord<String, byte[]>> dlt = readDlt(Duration.ofSeconds(15));
        assertThat(dlt).hasSize(1);
        assertThat(attempts).hasValue(1);
        assertThat(meterRegistry.counter("notification.send.failed", "reason", "http_4xx").count()).isEqualTo(1.0);
    }

    @Test
    void invalidJsonGoesToTheDltAndDoesNotBlockTheNextMessage() {
        startListener(sent::add);

        producer.send(topic, "{isto não é json");
        producer.send(topic, validPayload());

        List<ConsumerRecord<String, byte[]>> dlt = readDlt(Duration.ofSeconds(15));
        await().atMost(Duration.ofSeconds(15)).until(() -> sent.size() == 1);
        assertThat(dlt).hasSize(1);
        assertThat(new String(dlt.get(0).value(), StandardCharsets.UTF_8)).isEqualTo("{isto não é json");
        assertThat(attempts).hasValue(0);
    }

    private void startListener(java.util.function.Consumer<String> onSend) {
        var kafkaProperties = new KafkaProperties();
        kafkaProperties.setBootstrapServers(List.of(broker.getBrokersAsString()));
        DefaultErrorHandler errorHandler = new KafkaErrorHandlingConfig()
                .kafkaErrorHandler(kafkaProperties, meterRegistry, 3, 50L, 2.0);
        var consumer = new NotificationCommandConsumer((tipo, to, params) -> onSend.accept(to), meterRegistry);

        var valueDeserializer = new ErrorHandlingDeserializer<>(
                new JsonDeserializer<>(SendEmailCommandDto.class).trustedPackages("*"));
        var consumerFactory = new DefaultKafkaConsumerFactory<String, SendEmailCommandDto>(
                Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString(),
                        ConsumerConfig.GROUP_ID_CONFIG, "notification-group-" + UUID.randomUUID(),
                        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), valueDeserializer);

        var containerProperties = new ContainerProperties(topic);
        containerProperties.setMessageListener((MessageListener<String, SendEmailCommandDto>) record ->
                consumer.consume(record.value(), null));
        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setCommonErrorHandler(errorHandler);
        container.start();
    }

    private List<ConsumerRecord<String, byte[]>> readDlt(Duration timeout) {
        var props = KafkaTestUtils.consumerProps(broker, "dlt-reader-" + UUID.randomUUID(), false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var factory = new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), new ByteArrayDeserializer());
        List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
        try (var dltConsumer = factory.createConsumer()) {
            dltConsumer.subscribe(List.of(topic + ".DLT"));
            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (System.currentTimeMillis() < deadline && records.isEmpty()) {
                dltConsumer.poll(Duration.ofMillis(200)).forEach(records::add);
            }
            // Janela extra pra pegar mensagens inesperadas além da primeira.
            dltConsumer.poll(Duration.ofMillis(500)).forEach(records::add);
        }
        return records;
    }

    private static String header(ConsumerRecord<?, ?> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private static String validPayload() {
        return "{\"to\":\"cliente@petshop.com\",\"tipo\":\"" + TipoNotificacao.RESET_SENHA + "\",\"params\":{}}";
    }

    private static FeignException brevoError(int status) {
        var request = Request.create(Request.HttpMethod.POST, "http://brevo/smtp/email", Map.of(),
                new byte[0], StandardCharsets.UTF_8, new RequestTemplate());
        return FeignException.errorStatus("enviarEmail",
                Response.builder().status(status).reason("erro").request(request).headers(Map.of()).build());
    }
}
