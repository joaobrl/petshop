package com.petshop.staff_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtService;
import com.petshop.commons.web.CorrelationIdFilter;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A única chamada Feign deste serviço ({@code StaffRegistryFeign}, pro
 * customer-management) é uma consulta de sistema — "quem é staff e qual o
 * cargo" — não uma ação em nome de um usuário específico. Por isso, ao
 * contrário do booking-service/order-service (que fazem token relay), aqui
 * é sempre token de serviço: repassar o token de quem originou o pedido
 * faria a consulta cair em {@code GET /api/v1/staff/all}, que exige ADMIN —
 * e um cliente nunca tem essa role.
 */
@Configuration
@RequiredArgsConstructor
public class FeignTokenRelayConfig {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String SERVICE_NAME = "staff-service";

    private final JwtService jwtService;

    @Bean
    public RequestInterceptor tokenRelayInterceptor() {
        return requestTemplate -> {
            requestTemplate.header(AUTHORIZATION_HEADER, "Bearer " + jwtService.generateServiceToken(SERVICE_NAME));
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                requestTemplate.header(CorrelationIdFilter.HEADER, correlationId);
            }
        };
    }
}
