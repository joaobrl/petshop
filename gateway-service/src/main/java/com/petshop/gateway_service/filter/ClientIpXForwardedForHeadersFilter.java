package com.petshop.gateway_service.filter;

import org.springframework.cloud.gateway.filter.headers.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;

/**
 * Emite {@code X-Forwarded-For} com <b>só</b> o IP do peer TCP que chamou o
 * gateway (SDD-028). O gateway é a borda: não há proxy confiável na frente,
 * então qualquer {@code X-Forwarded-For} recebido foi escrito pelo próprio
 * cliente e é descartado — senão um valor forjado poderia chegar ao
 * downstream (o rate limit de login do customer-management lê essa cadeia).
 *
 * <p>Por que não só {@code trusted-proxies} (verificado no bytecode do
 * spring-cloud-gateway-server-webflux 5.0.2): o {@code XForwardedHeadersFilter}
 * apaga o header inteiro, sem acrescentar o peer, quando o peer não casa com
 * a regex; quando casa, acrescenta o peer mas mantém à esquerda o que o
 * cliente mandou. Sem a propriedade, o {@code RemoveXForwardedHeadersFilter}
 * apaga os {@code X-Forwarded-*} — este filtro roda depois dele e escreve o
 * header de novo, agora com o valor certo.
 *
 * <p>Quando existir um LB/ingress na frente, este filtro precisa passar a
 * confiar nele (manter a cadeia recebida só se o peer for o LB) — ver o
 * comentário em {@code application.yaml}.
 */
@Component
public class ClientIpXForwardedForHeadersFilter implements HttpHeadersFilter, Ordered {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    @Override
    public HttpHeaders filter(HttpHeaders input, ServerWebExchange exchange) {
        var updated = new HttpHeaders();
        input.forEach((name, values) -> {
            if (!X_FORWARDED_FOR.equalsIgnoreCase(name)) {
                updated.addAll(name, values);
            }
        });

        var peerIp = peerIp(exchange.getRequest().getRemoteAddress());
        // Sem endereço do peer (não acontece com Netty): melhor não mandar
        // header nenhum do que deixar passar o que veio do cliente — o
        // downstream conta pelo IP do gateway.
        if (peerIp != null) {
            updated.set(X_FORWARDED_FOR, peerIp);
        }
        return updated;
    }

    // IP literal do socket, nunca hostname: getAddress() já vem resolvido
    // pelo Netty, sem lookup de DNS.
    private static String peerIp(InetSocketAddress remote) {
        if (remote == null || remote.getAddress() == null) {
            return null;
        }
        return remote.getAddress().getHostAddress();
    }

    // Último da cadeia de HttpHeadersFilter: roda depois do
    // RemoveXForwardedHeadersFilter (ordem 0) e de qualquer outro que mexa em
    // X-Forwarded-*, então a palavra final sobre o header é sempre esta.
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
