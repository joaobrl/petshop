package com.petshop.booking_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtService;
import com.petshop.commons.web.CorrelationIdFilter;
import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * "Token relay": propaga o header Authorization da requisição recebida pra chamada Feign de saída (staff-service).
 * Sem token pra propagar (ex.: {@code GET /available-slots}, público), assina um token de serviço local
 * ({@link JwtService#generateServiceToken}) em vez de mandar sem Authorization — senão o staff-service rejeitava
 * com 401 qualquer consulta feita por visitante anônimo.
 */
@Configuration
@RequiredArgsConstructor
public class FeignTokenRelayConfig {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String SERVICE_NAME = "booking-service";

    private final JwtService jwtService;

    @Bean
    public RequestInterceptor tokenRelayInterceptor() {
        return requestTemplate -> {
            String bearerToken = relayedUserToken().orElseGet(this::serviceToken);
            requestTemplate.header(AUTHORIZATION_HEADER, bearerToken);
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                requestTemplate.header(CorrelationIdFilter.HEADER, correlationId);
            }
        };
    }

    private Optional<String> relayedUserToken() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return Optional.empty();
        }
        HttpServletRequest request = attributes.getRequest();
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        return (authorization == null || authorization.isBlank()) ? Optional.empty() : Optional.of(authorization);
    }

    private String serviceToken() {
        return "Bearer " + jwtService.generateServiceToken(SERVICE_NAME);
    }
}
