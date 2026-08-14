package com.petshop.order_service.infrastructure.messaging.adapter;

import com.petshop.commons.messaging.KafkaTopics;
import com.petshop.commons.web.CorrelationIdFilter;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.infrastructure.messaging.dto.SendEmailCommandDto;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationKafkaAdapterOutTest {

    private static final String TOPIC = KafkaTopics.NOTIFICATION_COMMANDS;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private NotificationKafkaAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new NotificationKafkaAdapterOut(kafkaTemplate);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private Order sampleOrder() {
        var cart = Cart.openFor(UUID.randomUUID(), 24);
        cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);
        var order = Order.fromCart(cart);
        order.setId(UUID.randomUUID());
        return order;
    }

    @SuppressWarnings("unchecked")
    private ProducerRecord<String, Object> capturedRecord() {
        ArgumentCaptor<ProducerRecord<String, Object>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void sendOrderAwaitingPaymentPublishesExpectedTypeAndParams() {
        var order = sampleOrder();

        adapter.sendOrderAwaitingPayment(order, "maria@mail.com", "Maria");

        var record = capturedRecord();
        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(record.key()).isEqualTo(order.getId().toString());
        var command = (SendEmailCommandDto) record.value();
        assertThat(command.to()).isEqualTo("maria@mail.com");
        assertThat(command.tipo()).isEqualTo("PEDIDO_AGUARDANDO_PAGAMENTO");
        assertThat(command.params())
                .containsEntry("customerName", "Maria")
                .containsEntry("orderId", order.getId().toString())
                .containsKey("totalAmount");
    }

    @Test
    void sendPaymentConfirmedPublishesExpectedTypeAndParams() {
        var order = sampleOrder();

        adapter.sendPaymentConfirmed(order, "maria@mail.com", "Maria");

        var record = capturedRecord();
        assertThat(record.key()).isEqualTo(order.getId().toString());
        var command = (SendEmailCommandDto) record.value();
        assertThat(command.tipo()).isEqualTo("PAGAMENTO_CONFIRMADO");
        assertThat(command.params()).containsKey("totalAmount");
    }

    @Test
    void sendInvoiceIssuedPublishesExpectedTypeAndParams() {
        var order = sampleOrder();

        adapter.sendInvoiceIssued(order, "maria@mail.com", "Maria");

        var record = capturedRecord();
        assertThat(record.key()).isEqualTo(order.getId().toString());
        var command = (SendEmailCommandDto) record.value();
        assertThat(command.tipo()).isEqualTo("NOTA_FISCAL_EMITIDA");
        assertThat(command.params()).containsEntry("orderId", order.getId().toString());
    }

    @Test
    void sendPickupWindowPublishesExpectedTypeAndParams() {
        var order = sampleOrder();

        adapter.sendPickupWindow(order, "maria@mail.com", "Maria");

        var record = capturedRecord();
        assertThat(record.key()).isEqualTo(order.getId().toString());
        var command = (SendEmailCommandDto) record.value();
        assertThat(command.tipo()).isEqualTo("PEDIDO_EM_PREPARACAO");
    }

    @Test
    void sendOrderReadyForPickupPublishesExpectedTypeAndParams() {
        var order = sampleOrder();

        adapter.sendOrderReadyForPickup(order, "maria@mail.com", "Maria");

        var record = capturedRecord();
        assertThat(record.key()).isEqualTo(order.getId().toString());
        var command = (SendEmailCommandDto) record.value();
        assertThat(command.tipo()).isEqualTo("PEDIDO_PRONTO_RETIRADA");
    }

    @Test
    void sendCartReminderPublishesExpectedTypeAndParamsKeyedByCartId() {
        var cart = Cart.openFor(UUID.randomUUID(), 24);
        cart.addOrIncrementItem(1L, "Racao", 10.0, 2, true);

        adapter.sendCartReminder(cart, "maria@mail.com", "Maria");

        var record = capturedRecord();
        assertThat(record.topic()).isEqualTo(TOPIC);
        assertThat(record.key()).isEqualTo(cart.getId().toString());
        var command = (SendEmailCommandDto) record.value();
        assertThat(command.tipo()).isEqualTo("CARRINHO_ABANDONADO");
        assertThat(command.params())
                .containsEntry("itemCount", 1)
                .containsKey("cartTotal");
    }

    @Test
    void includesCorrelationIdKafkaHeaderWhenPresentInMdc() {
        var order = sampleOrder();
        MDC.put(CorrelationIdFilter.MDC_KEY, "corr-456");

        adapter.sendOrderAwaitingPayment(order, "maria@mail.com", "Maria");

        var header = capturedRecord().headers().lastHeader(CorrelationIdFilter.HEADER);
        assertThat(header).isNotNull();
        assertThat(new String(header.value(), StandardCharsets.UTF_8)).isEqualTo("corr-456");
    }

    @Test
    void omitsCorrelationIdHeaderWhenAbsentFromMdc() {
        var order = sampleOrder();

        adapter.sendOrderAwaitingPayment(order, "maria@mail.com", "Maria");

        assertThat(capturedRecord().headers().lastHeader(CorrelationIdFilter.HEADER)).isNull();
    }

    @Test
    void neverThrowsWhenKafkaTemplatePublishFails() {
        var order = sampleOrder();
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenThrow(new RuntimeException("broker down"));

        adapter.sendOrderAwaitingPayment(order, "maria@mail.com", "Maria");
    }
}
