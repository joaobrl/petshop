package com.petshop.commons.feign;

import com.petshop.commons.security.jwt.JwtService;

/**
 * Resolve o header Authorization a repassar nas chamadas Feign de saída.
 * {@code resolveAuthorizationHeader} repassa o token de quem chamou quando
 * há um (relay-com-fallback); {@code mintServiceToken} sempre assina um
 * token de serviço novo (rotas internas que não podem aceitar o token de um
 * cliente, ou serviços que só fazem consulta de sistema).
 * <p>
 * Cada serviço declara o bean, informando o próprio nome
 * ({@code spring.application.name}), que vai como subject do token de serviço.
 */
public class TokenPropagation {

    private final JwtService jwtService;
    private final String serviceName;

    public TokenPropagation(JwtService jwtService, String serviceName) {
        this.jwtService = jwtService;
        this.serviceName = serviceName;
    }

    public String resolveAuthorizationHeader(String token) {
        return (token == null || token.isBlank()) ? mintServiceToken() : token;
    }

    public String mintServiceToken() {
        return "Bearer " + jwtService.generateServiceToken(serviceName);
    }
}
