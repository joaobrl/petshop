package com.petshop.commons.security.oauth2;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeycloakJwtDecodersTest {

    private static final String ISSUER = "http://localhost:8180/realms/petshop";
    private static final String AUDIENCE = "order-service";

    private static RSAKey signingKey;
    private static HttpServer jwksServer;
    private static String jwkSetUri;

    @BeforeAll
    static void startJwksServer() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("petshop-test-key").generate();
        var jwks = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwksServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwksServer.createContext("/certs", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
        });
        jwksServer.start();
        jwkSetUri = "http://127.0.0.1:" + jwksServer.getAddress().getPort() + "/certs";
    }

    @AfterAll
    static void stopJwksServer() {
        jwksServer.stop(0);
    }

    private static Jwt jwt(String issuer, List<String> audience, Instant issuedAt, Instant expiresAt) {
        return Jwt.withTokenValue("raw.jwt.token")
                .header("alg", "RS256")
                .claim("iss", issuer)
                .claim("aud", audience)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
    }

    private static Jwt validJwt(String issuer, List<String> audience) {
        return jwt(issuer, audience, Instant.now(), Instant.now().plusSeconds(300));
    }

    private static JWTClaimsSet claims(String issuer, String audience) {
        return new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject("user-1")
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .build();
    }

    private static String signRs256(JWTClaimsSet claims) throws JOSEException {
        var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(), claims);
        jwt.sign(new RSASSASigner(signingKey));
        return jwt.serialize();
    }

    private static String signHs256(JWTClaimsSet claims) throws JOSEException {
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner("segredo-antigo-hs256-com-pelo-menos-32-bytes".getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    @Test
    @DisplayName("Deve decodificar o token RS256 assinado pela chave do JWKS quando iss e aud estiverem corretos")
    void shouldDecodeTokenWhenSignedByJwksKeyWithCorrectIssuerAndAudience() throws Exception {
        var decoder = KeycloakJwtDecoders.fromJwkSetUri(jwkSetUri, ISSUER, AUDIENCE);

        var decoded = decoder.decode(signRs256(claims(ISSUER, AUDIENCE)));

        assertThat(decoded.getSubject()).isEqualTo("user-1");
        assertThat(decoded.getAudience()).containsExactly(AUDIENCE);
    }

    @Test
    @DisplayName("Deve recusar no decoder o token cujo aud não contém o serviço")
    void shouldRejectInDecoderWhenAudienceDoesNotContainService() throws Exception {
        var decoder = KeycloakJwtDecoders.fromJwkSetUri(jwkSetUri, ISSUER, AUDIENCE);
        var token = signRs256(claims(ISSUER, "customer-management"));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("Deve recusar no decoder o token cujo iss é diferente do esperado")
    void shouldRejectInDecoderWhenIssuerIsWrong() throws Exception {
        var decoder = KeycloakJwtDecoders.fromJwkSetUri(jwkSetUri, ISSUER, AUDIENCE);
        var token = signRs256(claims("http://evil.example/realms/petshop", AUDIENCE));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    @DisplayName("Deve recusar o token HS256 quando o decoder só aceita RS256")
    void shouldRejectHs256TokenWhenDecoderOnlyAcceptsRs256() throws Exception {
        var decoder = KeycloakJwtDecoders.fromJwkSetUri(jwkSetUri, ISSUER, AUDIENCE);
        var token = signHs256(claims(ISSUER, AUDIENCE));

        assertThatThrownBy(() -> decoder.decode(token))
                .isInstanceOf(BadJwtException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("Deve lançar IllegalArgumentException quando o jwkSetUri for vazio")
    void shouldThrowIllegalArgumentWhenJwkSetUriIsBlank(String blankJwkSetUri) {
        assertThatThrownBy(() -> KeycloakJwtDecoders.fromJwkSetUri(blankJwkSetUri, ISSUER, AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jwk-set-uri");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("Deve lançar IllegalArgumentException quando o issuerUri for vazio")
    void shouldThrowIllegalArgumentWhenIssuerUriIsBlank(String issuerUri) {
        assertThatThrownBy(() -> KeycloakJwtDecoders.validator(issuerUri, AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer-uri");
        assertThatThrownBy(() -> KeycloakJwtDecoders.fromJwkSetUri(jwkSetUri, issuerUri, AUDIENCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issuer-uri");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("Deve lançar IllegalArgumentException quando a audience for vazia")
    void shouldThrowIllegalArgumentWhenAudienceIsBlank(String audience) {
        assertThatThrownBy(() -> KeycloakJwtDecoders.validator(ISSUER, audience))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience");
        assertThatThrownBy(() -> KeycloakJwtDecoders.fromJwkSetUri(jwkSetUri, ISSUER, audience))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience");
    }

    @Test
    @DisplayName("Deve recusar o token quando o iss for diferente do esperado")
    void shouldRejectTokenWhenIssuerIsWrong() {
        var validator = KeycloakJwtDecoders.validator(ISSUER, AUDIENCE);

        var result = validator.validate(validJwt("http://evil.example/realms/petshop", List.of(AUDIENCE)));

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Deve recusar o token quando o aud não contiver o serviço")
    void shouldRejectTokenWhenAudienceDoesNotContainService() {
        var validator = KeycloakJwtDecoders.validator(ISSUER, AUDIENCE);

        var result = validator.validate(validJwt(ISSUER, List.of("customer-management")));

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Deve aceitar o token quando iss e aud estiverem corretos")
    void shouldAcceptTokenWhenIssuerAndAudienceAreCorrect() {
        var validator = KeycloakJwtDecoders.validator(ISSUER, AUDIENCE);

        var result = validator.validate(validJwt(ISSUER, List.of("customer-management", AUDIENCE)));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Deve recusar o token quando ele estiver expirado")
    void shouldRejectTokenWhenExpired() {
        var validator = KeycloakJwtDecoders.validator(ISSUER, AUDIENCE);
        var expired = jwt(ISSUER, List.of(AUDIENCE), Instant.now().minusSeconds(1200), Instant.now().minusSeconds(600));

        var result = validator.validate(expired);

        assertThat(result.hasErrors()).isTrue();
    }
}
