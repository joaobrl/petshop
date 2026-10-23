package com.petshop.order_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolve o header Authorization a repassar nas chamadas Feign de saída.
 * {@code resolveAuthorizationHeader} repassa o token de quem chamou quando
 * há um (relay-com-fallback); {@code mintServiceToken} sempre assina um
 * token de serviço novo, usado nas rotas internas do product-registration
 * (reserve/release/confirm) que não podem aceitar o token de um cliente.
 */
@Component
@RequiredArgsConstructor
public class TokenPropagation {

    private static final String SERVICE_NAME = "order-service";

    private final JwtService jwtService;

    public String resolveAuthorizationHeader(String token) {
        return (token == null || token.isBlank()) ? mintServiceToken() : token;
    }

    public String mintServiceToken() {
        return "Bearer " + jwtService.generateServiceToken(SERVICE_NAME);
    }
}
