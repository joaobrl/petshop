package com.petshop.order_service.infrastructure.security;

import com.petshop.commons.web.CorrelationIdFilter;
import feign.RequestInterceptor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O Authorization das chamadas Feign agora é explícito (parâmetro
 * {@code @RequestHeader} em cada método, ver TokenPropagation). Só o
 * correlation-id continua sendo propagado via interceptor.
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
