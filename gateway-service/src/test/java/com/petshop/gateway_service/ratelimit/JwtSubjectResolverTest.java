package com.petshop.gateway_service.ratelimit;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSubjectResolverTest {

    private static final String SECRET = "test-secret-key-for-jwt-signing-min-32-bytes-long";

    private static String bearer(String subject) {
        var token = Jwts.builder()
                .subject(subject)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        return "Bearer " + token;
    }

    @Test
    @DisplayName("Deve devolver o sub quando a assinatura do token for válida")
    void shouldReturnSubjectWhenSignatureIsValid() {
        var resolver = new JwtSubjectResolver(SECRET);

        var subject = resolver.resolveSubject(bearer("user-123"));

        assertThat(subject).contains("user-123");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "segredo-curto"})
    @DisplayName("Deve ignorar o token quando o segredo estiver ausente ou curto demais")
    void shouldReturnEmptyWhenSecretIsMissingOrShort(String secret) {
        var resolver = new JwtSubjectResolver(secret);

        var subject = resolver.resolveSubject(bearer("user-123"));

        assertThat(subject).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Basic dXNlcjpzZW5oYQ==", "token-sem-prefixo"})
    @DisplayName("Deve ignorar o header quando ele não for Bearer")
    void shouldReturnEmptyWhenHeaderIsNotBearer(String header) {
        var resolver = new JwtSubjectResolver(SECRET);

        var subject = resolver.resolveSubject(header);

        assertThat(subject).isEmpty();
    }

    @Test
    @DisplayName("Deve ignorar o token quando ele não tiver sub")
    void shouldReturnEmptyWhenSubjectIsMissing() {
        var resolver = new JwtSubjectResolver(SECRET);
        var token = Jwts.builder()
                .claim("role", "CUSTOMER")
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        var subject = resolver.resolveSubject("Bearer " + token);

        assertThat(subject).isEmpty();
    }
}
