package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.config;

import com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto.BookingEventDTO;
import com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto.OrderCompletedEventDTO;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

/**
 * `BookingHistoryKafkaListener` e `OrderHistoryKafkaListener` não podem
 * compartilhar a mesma {@code ConcurrentKafkaListenerContainerFactory}
 * genérica (a que o Boot autoconfigura a partir de
 * {@code spring.kafka.consumer.*}): com {@code spring.json.use.type.headers:
 * false} (deliberado — o producer de cada evento é outro serviço, com sua
 * própria classe de DTO, empacotada diferente da nossa; nunca deveríamos
 * tentar carregar o nome de classe *dele* aqui), o {@code JsonDeserializer}
 * não tem como saber em qual tipo desserializar sem um alvo fixo — e cada
 * tópico precisa de um tipo diferente ({@code BookingEventDTO} vs.
 * {@code OrderCompletedEventDTO}). Sem isso, TODA mensagem — mesmo um JSON
 * perfeitamente válido — falha com "No type information in headers and no
 * default type provided" e vai pro dead-letter-topic à toa (achado ao
 * reverificar o comportamento do DLT ao vivo: uma mensagem válida de
 * {@code booking-completed} publicada de teste caiu lá com esse erro).
 * <p>
 * Cada factory abaixo passa o {@code JsonDeserializer} já construído com o
 * tipo-alvo fixo (construtor {@code JsonDeserializer(Class)}), o que faz o
 * deserializer ignorar completamente os headers de tipo (nem precisa mais
 * do {@code spring.json.use.type.headers: false} pra isso — o construtor já
 * tem esse efeito) — e embrulha em {@link ErrorHandlingDeserializer} pra
 * manter a proteção contra poison pill do {@link KafkaErrorHandlingConfig}.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> bookingCompletedKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties, DefaultErrorHandler kafkaErrorHandler) {
        return containerFactory(kafkaProperties, kafkaErrorHandler, BookingEventDTO.class);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> orderCompletedKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties, DefaultErrorHandler kafkaErrorHandler) {
        return containerFactory(kafkaProperties, kafkaErrorHandler, OrderCompletedEventDTO.class);
    }

    @SuppressWarnings("unchecked")
    private <T> ConcurrentKafkaListenerContainerFactory<String, Object> containerFactory(
            KafkaProperties kafkaProperties, DefaultErrorHandler kafkaErrorHandler, Class<T> targetType) {
        // Cast seguro: Deserializer<T> e Deserializer<Object> têm exatamente a
        // mesma representação em runtime (type erasure) — só o compilador não
        // consegue expressar "T que sabemos ser compatível com Object" sem
        // isso, já que DefaultKafkaConsumerFactory<String, Object> precisa do
        // parâmetro fixo em Object pra caber num ConsumerFactory<String, Object>
        // genérico compartilhado pelas duas factories (uma por tipo de evento).
        Deserializer<Object> valueDeserializer = (Deserializer<Object>) (Deserializer<?>)
                new ErrorHandlingDeserializer<>(new JsonDeserializer<>(targetType).trustedPackages("*"));
        Deserializer<String> keyDeserializer = new ErrorHandlingDeserializer<>(new StringDeserializer());

        ConsumerFactory<String, Object> consumerFactory = new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(), keyDeserializer, valueDeserializer);

        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
