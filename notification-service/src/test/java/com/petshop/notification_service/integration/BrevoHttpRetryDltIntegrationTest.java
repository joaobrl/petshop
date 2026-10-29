package com.petshop.notification_service.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.petshop.notification_service.adapter.out.brevo.BrevoEmailAdapterOut;
import com.petshop.notification_service.core.application.port.out.NotificationSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * CA6/CA7 do SDD-004: contexto Spring completo (listener Kafka real,
 * {@code DefaultErrorHandler} real, {@code BrevoFeignClient} real) contra um
 * Brevo falso em WireMock e um Kafka embarcado. Confirma que o tipo de
 * exceção que o Feign realmente lança é classificado como a spec pede.
 */
@SpringBootTest(properties = {
        "spring.kafka.properties.security.protocol=PLAINTEXT",
        "spring.kafka.properties.sasl.mechanism=",
        "spring.kafka.properties.sasl.jaas.config=",
        "notification.kafka.retry.initial-interval-ms=50",
        "brevo.api-key=chave-de-teste",
        "brevo.templates.RESET_SENHA=7"
})
@EmbeddedKafka(partitions = 1, topics = {"notification-commands", "notification-commands.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class BrevoHttpRetryDltIntegrationTest {

    private static final WireMockServer BREVO = new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        BREVO.start();
    }

    @AfterAll
    static void stopBrevo() {
        BREVO.stop();
    }

    @DynamicPropertySource
    static void brevoUrl(DynamicPropertyRegistry registry) {
        registry.add("brevo.base-url", () -> "http://localhost:" + BREVO.port());
    }

    @Autowired
    private NotificationSender sender;
    @Autowired
    private EmbeddedKafkaBroker broker;

    @BeforeEach
    void resetBrevo() {
        BREVO.resetAll();
    }

    private static long brevoCalls() {
        return BREVO.countRequestsMatching(postRequestedFor(urlEqualTo("/smtp/email")).build()).getCount();
    }

    @Test
    void usesTheRealBrevoAdapter() {
        assertThat(sender).isInstanceOf(BrevoEmailAdapterOut.class);
    }

    @Test
    void ca6_brevo503IsRetriedFourTimesAndThenGoesToTheDlt() {
        BREVO.stubFor(post(urlEqualTo("/smtp/email")).willReturn(aResponse().withStatus(503)));

        long dltStart = BrevoRetryDltIntegrationSupport.dltEndOffset(broker);
        BrevoRetryDltIntegrationSupport.sendCommand(broker);

        var dlt = BrevoRetryDltIntegrationSupport.readDlt(broker, dltStart, Duration.ofSeconds(20));
        assertThat(dlt).hasSize(1);
        assertThat(brevoCalls()).isEqualTo(4);
        assertThat(new String(dlt.get(0).headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN).value(),
                StandardCharsets.UTF_8)).contains("FeignException");
    }

    @Test
    void ca7_brevo400GoesStraightToTheDltWithASingleHttpCall() {
        BREVO.stubFor(post(urlEqualTo("/smtp/email")).willReturn(aResponse().withStatus(400)));

        long dltStart = BrevoRetryDltIntegrationSupport.dltEndOffset(broker);
        BrevoRetryDltIntegrationSupport.sendCommand(broker);

        var dlt = BrevoRetryDltIntegrationSupport.readDlt(broker, dltStart, Duration.ofSeconds(20));
        assertThat(dlt).hasSize(1);
        assertThat(brevoCalls()).isEqualTo(1);
    }

    @Test
    void brevo503ThenSuccessDoesNotGoToTheDlt() {
        BREVO.stubFor(post(urlEqualTo("/smtp/email")).inScenario("flap")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503)).willSetStateTo("up"));
        BREVO.stubFor(post(urlEqualTo("/smtp/email")).inScenario("flap").whenScenarioStateIs("up")
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"messageId\":\"x\"}")));

        long dltStart = BrevoRetryDltIntegrationSupport.dltEndOffset(broker);
        BrevoRetryDltIntegrationSupport.sendCommand(broker);

        await().atMost(Duration.ofSeconds(15)).until(() -> brevoCalls() == 2);
        assertThat(BrevoRetryDltIntegrationSupport.readDlt(broker, dltStart, Duration.ofSeconds(2))).isEmpty();
    }
}
