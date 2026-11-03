package com.petshop.order_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolve o header Authorization a repassar nas chamadas Feign de saída.
 * {@code resolveAuthorizationHeader} repassa o token do usuário autenticado
 * na requisição atual (já validado pelo JwtAuthenticationFilter), senão um
 * token de serviço (relay-com-fallback); {@code mintServiceToken} sempre assina um
 * token de serviço novo, usado nas rotas internas do product-registration
 * (reserve/release/confirm) que não podem aceitar o token de um cliente.
 */
@Component
@RequiredArgsConstructor
public class TokenPropagation {

    private static final String SERVICE_NAME = "order-service";

    private final JwtService jwtService;

    /**
     * Repassa o token guardado como credentials pelo JwtAuthenticationFilter.
     * Sem autenticação (anônimo, token inválido em rota pública, scheduler),
     * assina um token de serviço. Nunca repassa string não verificada.
     */
    public String resolveAuthorizationHeader() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        // Só o que o JwtAuthenticationFilter cria: AnonymousAuthenticationToken (rota
        // pública sem token) também é isAuthenticated(), mas com credentials "".
        if (auth instanceof UsernamePasswordAuthenticationToken
                && auth.isAuthenticated()
                && auth.getCredentials() instanceof String raw
                && !raw.isBlank()) {
            return "Bearer " + raw;
        }
        return mintServiceToken();
    }

    public String mintServiceToken() {
        return "Bearer " + jwtService.generateServiceToken(SERVICE_NAME);
    }
}
