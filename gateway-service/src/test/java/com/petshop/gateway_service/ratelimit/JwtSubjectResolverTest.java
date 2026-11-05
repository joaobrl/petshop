package com.petshop.gateway_service.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtSubjectResolverTest {

    private static String bearer(String subject) {
        return "Bearer " + TestJwts.validToken(subject);
    }

    @Test
    @DisplayName("Deve devolver o sub quando a assinatura do token for válida")
    void shouldReturnSubjectWhenSignatureIsValid() {
        var resolver = TestJwts.resolver();

        var subject = resolver.resolveSubject(bearer("user-123")).blockOptional();

        assertThat(subject).contains("user-123");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Deve ignorar o token quando o Keycloak não estiver configurado")
    void shouldReturnEmptyWhenKeycloakIsNotConfigured(String jwkSetUri) {
        var resolver = new JwtSubjectResolver(jwkSetUri, TestJwts.ISSUER);

        var subject = resolver.resolveSubject(bearer("user-123")).blockOptional();

        assertThat(subject).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"Basic dXNlcjpzZW5oYQ==", "token-sem-prefixo"})
    @DisplayName("Deve ignorar o header quando ele não for Bearer")
    void shouldReturnEmptyWhenHeaderIsNotBearer(String header) {
        var resolver = TestJwts.resolver();

        var subject = resolver.resolveSubject(header).blockOptional();

        assertThat(subject).isEmpty();
    }

    @Test
    @DisplayName("Deve ignorar o token quando ele não tiver sub")
    void shouldReturnEmptyWhenSubjectIsMissing() {
        var resolver = TestJwts.resolver();
        var token = TestJwts.token(TestJwts.KEYS, null, TestJwts.ISSUER, Instant.now().plusSeconds(3600));

        var subject = resolver.resolveSubject("Bearer " + token).blockOptional();

        assertThat(subject).isEmpty();
    }
}
