package com.petshop.gateway_service.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * Chave do rate limit (SDD-022): {@code user:<sub>} quando o Bearer tem
 * assinatura válida, senão {@code ip:<ip>}.
 *
 * <p>O IP é o do peer TCP: o gateway é a borda (não há LB/ingress na frente,
 * ver SDD-028), então um {@code X-Forwarded-For} vindo do cliente não tem
 * valor e é ignorado aqui — senão bastaria variar o header para ganhar um
 * balde novo. Se um dia houver proxy confiável na frente, é aqui que muda.
 */
@Component
public class ClientKeyResolver implements KeyResolver {

    static final String USER_PREFIX = "user:";
    static final String IP_PREFIX = "ip:";
    private static final String UNKNOWN_IP = "unknown";

    private final JwtSubjectResolver jwtSubjectResolver;

    public ClientKeyResolver(JwtSubjectResolver jwtSubjectResolver) {
        this.jwtSubjectResolver = jwtSubjectResolver;
    }

    /** Usuário autenticado (assinatura válida) ou, na falta dele, IP. */
    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return jwtSubjectResolver.resolveSubject(authorization)
                .map(sub -> USER_PREFIX + sub)
                .switchIfEmpty(Mono.fromSupplier(() -> resolveIp(exchange)));
    }

    /** Sempre por IP, mesmo com token (grupos públicos). */
    public Mono<String> resolveByIp(ServerWebExchange exchange) {
        return Mono.just(resolveIp(exchange));
    }

    private static String resolveIp(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null) {
            return IP_PREFIX + UNKNOWN_IP;
        }
        return IP_PREFIX + (remote.getAddress() != null
                ? remote.getAddress().getHostAddress()
                : remote.getHostString());
    }
}
