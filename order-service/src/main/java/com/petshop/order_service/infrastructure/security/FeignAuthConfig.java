package com.petshop.order_service.infrastructure.security;

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
 * Se há requisição HTTP em andamento, repassa o token de quem chamou
 * (token relay); senão (chamada de scheduler, sem usuário autenticado),
 * assina um token de serviço localmente (mesmo segredo compartilhado que
 * os outros serviços usam pra validar) — sem round-trip de rede, então
 * nem precisa de cache.
 */
@Configuration
@RequiredArgsConstructor
public class FeignAuthConfig {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String SERVICE_NAME = "order-service";

    private final JwtService jwtService;

    @Bean
    public RequestInterceptor feignAuthInterceptor() {
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
        var attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
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
