package com.petshop.gateway_service.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import reactor.core.publisher.Mono;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class JwtSubjectResolverTest {

    @Mock
    private ReactiveJwtDecoder decoder;

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

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Deve ignorar o token quando o issuer-uri não estiver configurado")
    void shouldReturnEmptyWhenIssuerUriIsNotConfigured(String issuerUri) {
        var resolver = new JwtSubjectResolver("http://localhost:8180/realms/petshop/protocol/openid-connect/certs", issuerUri);

        var subject = resolver.resolveSubject(bearer("user-123")).blockOptional();

        assertThat(subject).isEmpty();
    }

    @Test
    @DisplayName("Deve ignorar o token malformado quando o Keycloak estiver configurado")
    void shouldReturnEmptyWhenTokenIsMalformedAndKeycloakIsConfigured() {
        var resolver = new JwtSubjectResolver("http://localhost:8180/realms/petshop/protocol/openid-connect/certs", TestJwts.ISSUER);

        var subject = resolver.resolveSubject("Bearer nao-e-um-jwt").blockOptional();

        assertThat(subject).isEmpty();
    }

    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                Arguments.of("assinado com outra chave",
                        TestJwts.token(TestJwts.OTHER_KEYS, "user-123", TestJwts.ISSUER, Instant.now().plusSeconds(3600))),
                Arguments.of("com iss de outro emissor",
                        TestJwts.token(TestJwts.KEYS, "user-123", "http://evil.example/realms/petshop", Instant.now().plusSeconds(3600))),
                Arguments.of("expirado",
                        TestJwts.token(TestJwts.KEYS, "user-123", TestJwts.ISSUER, Instant.now().minusSeconds(3600))),
                Arguments.of("malformado", "nao-e-um-jwt"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokens")
    @DisplayName("Deve ignorar o token quando ele for inválido")
    void shouldReturnEmptyWhenTokenIsInvalid(String description, String token) {
        var resolver = TestJwts.resolver();

        var subject = resolver.resolveSubject("Bearer " + token).blockOptional();

        assertThat(subject).isEmpty();
    }

    @Test
    @DisplayName("Deve devolver vazio sem propagar erro quando o decoder falhar")
    void shouldReturnEmptyWhenDecoderFails() {
        given(decoder.decode(anyString())).willReturn(Mono.error(new BadJwtException("assinatura inválida")));
        var resolver = new JwtSubjectResolver(decoder);

        var subject = resolver.resolveSubject("Bearer qualquer").blockOptional();

        assertThat(subject).isEmpty();
        then(decoder).should().decode("qualquer");
    }

    @Test
    @DisplayName("Deve devolver vazio quando o decoder lançar exceção ao decodificar")
    void shouldReturnEmptyWhenDecoderThrows() {
        given(decoder.decode(anyString())).willThrow(new IllegalStateException("jwks indisponível"));
        var resolver = new JwtSubjectResolver(decoder);

        var subject = resolver.resolveSubject("Bearer qualquer").blockOptional();

        assertThat(subject).isEmpty();
    }

    @Test
    @DisplayName("Deve devolver o sub quando o decoder devolver um jwt válido")
    void shouldReturnSubjectWhenDecoderReturnsJwt() {
        var jwt = Jwt.withTokenValue("qualquer").header("alg", "RS256").subject("user-456").build();
        given(decoder.decode(anyString())).willReturn(Mono.just(jwt));
        var resolver = new JwtSubjectResolver(decoder);

        var subject = resolver.resolveSubject("Bearer qualquer").blockOptional();

        assertThat(subject).contains("user-456");
    }

    @Test
    @DisplayName("Deve ignorar o token quando o sub estiver em branco")
    void shouldReturnEmptyWhenSubjectIsBlank() {
        var jwt = Jwt.withTokenValue("qualquer").header("alg", "RS256").subject("  ").build();
        given(decoder.decode(anyString())).willReturn(Mono.just(jwt));
        var resolver = new JwtSubjectResolver(decoder);

        var subject = resolver.resolveSubject("Bearer qualquer").blockOptional();

        assertThat(subject).isEmpty();
    }
}
