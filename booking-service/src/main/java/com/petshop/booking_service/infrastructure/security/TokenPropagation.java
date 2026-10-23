package com.petshop.booking_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolve o header Authorization a repassar na chamada Feign pro staff-service.
 * {@code resolveAuthorizationHeader} repassa o token de quem chamou quando há
 * um (relay-com-fallback); sem requisição HTTP em curso (ex.: disparado por
 * um scheduler), assina um token de serviço novo.
 */
@Component
@RequiredArgsConstructor
public class TokenPropagation {

    private static final String SERVICE_NAME = "booking-service";

    private final JwtService jwtService;

    public String resolveAuthorizationHeader(String token) {
        return (token == null || token.isBlank()) ? mintServiceToken() : token;
    }

    public String mintServiceToken() {
        return "Bearer " + jwtService.generateServiceToken(SERVICE_NAME);
    }
}
