package com.petshop.gateway_service.ratelimit;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ClientKeyResolverTest {

    private static final String SECRET = "test-secret-key-for-jwt-signing-min-32-bytes-long";
    private static final String OTHER_SECRET = "outro-segredo-com-pelo-menos-32-bytes-de-tamanho";
    private static final InetSocketAddress CLIENT = new InetSocketAddress("10.0.0.1", 54321);

    private final ClientKeyResolver resolver = new ClientKeyResolver(new JwtSubjectResolver(SECRET));

    private static String token(String secret, Instant expiration) {
        return Jwts.builder()
                .subject("user-123")
                .expiration(Date.from(expiration))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    private static MockServerWebExchange exchange(String authorization, InetSocketAddress remote) {
        var request = MockServerHttpRequest.get("/api/v1/bookings/list").remoteAddress(remote);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return MockServerWebExchange.from(request);
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                Arguments.of("Bearer lixo"),
                Arguments.of("Bearer " + token(OTHER_SECRET, Instant.now().plusSeconds(3600))),
                Arguments.of("Bearer " + token(SECRET, Instant.now().minusSeconds(60))));
    }

    @Test
    @DisplayName("Deve usar a chave do usuário quando o token for válido")
    void shouldResolveUserKeyWhenTokenIsValid() {
        var exchange = exchange("Bearer " + token(SECRET, Instant.now().plusSeconds(3600)), CLIENT);

        var key = resolver.resolve(exchange).block();

        assertThat(key).isEqualTo("user:user-123");
    }

    @Test
    @DisplayName("Deve usar a chave do IP quando não houver token")
    void shouldResolveIpKeyWhenTokenIsAbsent() {
        var key = resolver.resolve(exchange(null, CLIENT)).block();

        assertThat(key).isEqualTo("ip:10.0.0.1");
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    @DisplayName("Deve usar a chave do IP quando o token for malformado, de outra assinatura ou expirado")
    void shouldResolveIpKeyWhenTokenIsInvalid(String authorization) {
        var key = resolver.resolve(exchange(authorization, CLIENT)).block();

        assertThat(key).isEqualTo("ip:10.0.0.1");
    }

    @Test
    @DisplayName("Deve usar IP desconhecido quando a requisição não tiver endereço remoto")
    void shouldResolveUnknownIpWhenRemoteAddressIsMissing() {
        var key = resolver.resolve(exchange(null, null)).block();

        assertThat(key).isEqualTo("ip:unknown");
    }

    @Test
    @DisplayName("Deve usar o nome do host quando o endereço remoto não estiver resolvido")
    void shouldResolveHostStringWhenRemoteAddressIsUnresolved() {
        var key = resolver.resolve(exchange(null, InetSocketAddress.createUnresolved("cliente.local", 80))).block();

        assertThat(key).isEqualTo("ip:cliente.local");
    }

    @Test
    @DisplayName("Deve usar a chave do IP mesmo com token válido quando o grupo for por IP")
    void shouldResolveIpKeyWhenResolvingByIpWithValidToken() {
        var exchange = exchange("Bearer " + token(SECRET, Instant.now().plusSeconds(3600)), CLIENT);

        var key = resolver.resolveByIp(exchange).block();

        assertThat(key).isEqualTo("ip:10.0.0.1");
    }
}
