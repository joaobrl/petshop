package com.petshop.commons.feign;

import com.petshop.commons.security.jwt.JwtService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolve o header Authorization a repassar nas chamadas Feign de saída.
 * {@code resolveAuthorizationHeader} repassa o token do usuário autenticado
 * na requisição atual (já validado pelo JwtAuthenticationFilter), senão um
 * token de serviço (relay-com-fallback); {@code mintServiceToken} sempre assina um
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
        return "Bearer " + jwtService.generateServiceToken(serviceName);
    }
}
