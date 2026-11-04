package com.petshop.booking_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TokenPropagation {

    private static final String SERVICE_NAME = "booking-service";

    private final JwtService jwtService;

    /**
     * Repassa o token do usuário autenticado na requisição atual — o mesmo
     * que o JwtAuthenticationFilter validou e guardou como credentials. Sem
     * autenticação (anônimo, token inválido em rota pública, scheduler),
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
