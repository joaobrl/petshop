package com.petshop.order_service.infrastructure.messaging.adapter;

import com.petshop.commons.messaging.KafkaTopics;
import com.petshop.commons.web.CorrelationIdFilter;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.core.port.out.NotificationPortOut;
import com.petshop.order_service.infrastructure.messaging.dto.SendEmailCommandDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Publica comandos de e-mail no mesmo tópico que o booking-service usa
 * (notification-commands), consumido pelo notification-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationKafkaAdapterOut implements NotificationPortOut {

    // Precisam bater exatamente com os nomes das constantes de TipoNotificacao
    // no notification-service — ver tabela de mapeamento tipo → templateId lá.
    private static final String TIPO_PEDIDO_AGUARDANDO_PAGAMENTO = "PEDIDO_AGUARDANDO_PAGAMENTO";
    private static final String TIPO_PAGAMENTO_CONFIRMADO = "PAGAMENTO_CONFIRMADO";
    private static final String TIPO_NOTA_FISCAL_EMITIDA = "NOTA_FISCAL_EMITIDA";
    private static final String TIPO_PEDIDO_EM_PREPARACAO = "PEDIDO_EM_PREPARACAO";
    private static final String TIPO_PEDIDO_PRONTO_RETIRADA = "PEDIDO_PRONTO_RETIRADA";
    private static final String TIPO_CARRINHO_ABANDONADO = "CARRINHO_ABANDONADO";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void sendOrderAwaitingPayment(Order order, String customerEmail, String customerName) {
        var params = Map.<String, Object>of(
                "customerName", customerName,
                "orderId", order.getId().toString(),
                "totalAmount", formatCurrency(order.getTotalAmount())
        );
        publish(order.getId().toString(), customerEmail, TIPO_PEDIDO_AGUARDANDO_PAGAMENTO, params);
    }

    @Override
    public void sendPaymentConfirmed(Order order, String customerEmail, String customerName) {
        var params = Map.<String, Object>of(
                "customerName", customerName,
                "orderId", order.getId().toString(),
                "totalAmount", formatCurrency(order.getTotalAmount())
        );
        publish(order.getId().toString(), customerEmail, TIPO_PAGAMENTO_CONFIRMADO, params);
    }

    @Override
    public void sendInvoiceIssued(Order order, String customerEmail, String customerName) {
        var params = Map.<String, Object>of(
                "customerName", customerName,
                "orderId", order.getId().toString()
        );
        publish(order.getId().toString(), customerEmail, TIPO_NOTA_FISCAL_EMITIDA, params);
    }

    @Override
    public void sendPickupWindow(Order order, String customerEmail, String customerName) {
        var params = Map.<String, Object>of(
                "customerName", customerName,
                "orderId", order.getId().toString()
        );
        publish(order.getId().toString(), customerEmail, TIPO_PEDIDO_EM_PREPARACAO, params);
    }

    @Override
    public void sendOrderReadyForPickup(Order order, String customerEmail, String customerName) {
        var params = Map.<String, Object>of(
                "customerName", customerName,
                "orderId", order.getId().toString()
        );
        publish(order.getId().toString(), customerEmail, TIPO_PEDIDO_PRONTO_RETIRADA, params);
    }

    @Override
    public void sendCartReminder(Cart cart, String customerEmail, String customerName) {
        var params = Map.<String, Object>of(
                "customerName", customerName,
                "itemCount", cart.getItems().size(),
                "cartTotal", formatCurrency(cart.total())
        );
        publish(cart.getId().toString(), customerEmail, TIPO_CARRINHO_ABANDONADO, params);
    }

    // NumberFormat.getCurrencyInstance(pt-BR) usa NBSP (não espaço comum)
    // entre "R$" e o valor — formata manualmente pra garantir "R$ 45,90"
    // com espaço normal, igual ao esperado no template do Brevo.
    private String formatCurrency(double value) {
        return "R$ " + String.format(Locale.of("pt", "BR"), "%.2f", value);
    }

    private void publish(String key, String customerEmail, String tipo, Map<String, Object> params) {
        var command = new SendEmailCommandDto(customerEmail, tipo, params);
        try {
            var record = new ProducerRecord<String, Object>(
                    KafkaTopics.NOTIFICATION_COMMANDS, null, key, command, correlationIdHeaders());
            kafkaTemplate.send(record);
            log.info("Comando de e-mail '{}' publicado (key={})", tipo, key);
        } catch (Exception e) {
            log.error("Falha ao publicar comando de e-mail '{}' (key={})", tipo, key, e);
        }
    }

    // O consumer (notification-service) extrai este header de volta pro MDC dele,
    // pra conseguir seguir o e-mail nos logs até a chamada real ao Brevo.
    private List<Header> correlationIdHeaders() {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) {
            return List.of();
        }
        return List.of(new RecordHeader(CorrelationIdFilter.HEADER, correlationId.getBytes(StandardCharsets.UTF_8)));
    }
}
