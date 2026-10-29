package com.petshop.customermanagement.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Resolve o IP real do cliente para o rate limiting. X-Forwarded-For só é
 * levado em conta quando a conexão vem de um proxy da allowlist
 * (rate-limit.trusted-proxies) — vindo de qualquer outro lugar o header é
 * ignorado, senão o atacante trocaria de "IP" a cada requisição forjando-o.
 */
@Slf4j
public class ClientIpResolver {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern IPV6_CHARS = Pattern.compile("^[0-9a-fA-F:.]+$");

    private final List<IpAddressMatcher> trustedProxies;

    public ClientIpResolver(List<String> trustedProxyCidrs) {
        var cidrs = trustedProxyCidrs == null ? List.<String>of() : trustedProxyCidrs.stream()
                .map(String::trim)
                .filter(cidr -> !cidr.isEmpty())
                .toList();
        // CIDR inválido derruba o boot (IllegalArgumentException do matcher):
        // allowlist mal configurada em silêncio voltaria ao bug original.
        this.trustedProxies = cidrs.stream().map(IpAddressMatcher::new).toList();

        if (cidrs.isEmpty()) {
            log.info("Rate limit: nenhum proxy confiável configurado — X-Forwarded-For será ignorado");
        } else {
            log.info("Rate limit: proxies confiáveis carregados: {}", cidrs);
        }
    }

    public String resolve(HttpServletRequest request) {
        var remote = request.getRemoteAddr();
        if (!isTrusted(remote)) {
            return remote;
        }

        // Várias linhas de X-Forwarded-For equivalem a uma lista única
        // separada por vírgula (RFC 9110), na ordem em que chegaram.
        var headers = request.getHeaders(X_FORWARDED_FOR);
        var xff = headers == null ? "" : String.join(",", Collections.list(headers));
        if (xff.isBlank()) {
            return remote;
        }

        // Da direita para a esquerda: cada proxy confiável acrescenta quem o
        // chamou no fim, então o primeiro hop não confiável é o cliente real.
        // Tudo à esquerda dele pode ter sido forjado pelo próprio cliente.
        var hops = xff.split(",");
        for (int i = hops.length - 1; i >= 0; i--) {
            var ip = hops[i].trim();
            if (!isIpLiteral(ip)) {
                // Hop malformado onde deveria estar o IP anotado por um proxy
                // confiável: não dá pra confiar na cadeia, conta pelo proxy.
                return remote;
            }
            if (!isTrusted(ip)) {
                return ip;
            }
        }
        return remote;
    }

    private boolean isTrusted(String ip) {
        return ip != null && isIpLiteral(ip) && trustedProxies.stream().anyMatch(matcher -> matcher.matches(ip));
    }

    // Só aceita literais IPv4/IPv6 — nunca deixa um hostname chegar no
    // InetAddress (evita lookup de DNS disparado por header do cliente).
    private static boolean isIpLiteral(String value) {
        if (value.isEmpty()) {
            return false;
        }
        if (IPV4.matcher(value).matches()) {
            return true;
        }
        if (!value.contains(":") || !IPV6_CHARS.matcher(value).matches()) {
            return false;
        }
        try {
            InetAddress.getByName(value);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
