package com.petshop.commons.feign;

import com.petshop.commons.web.CorrelationIdFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Propaga o correlation-id (MDC) nas chamadas Feign de saída. O Authorization
 * é explícito (parâmetro {@code @RequestHeader} em cada método, ver
 * {@link TokenPropagation}). Os serviços fazem {@code @Import} desta classe.
 */
@Configuration
public class CorrelationIdFeignConfig {

    @Bean
    public RequestInterceptor correlationIdRequestInterceptor() {
        return requestTemplate -> {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                requestTemplate.header(CorrelationIdFilter.HEADER, correlationId);
            }
        };
    }
}
