package com.petshop.commons.security.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class KeycloakJwtDecodersTest {

    private static final String JWK_SET_URI = "http://localhost:8180/realms/petshop/protocol/openid-connect/certs";
    private static final String ISSUER = "http://localhost:8180/realms/petshop";
    private static final String AUDIENCE = "order-service";

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

    @Mock
    private NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder;
    @Mock
    private NimbusJwtDecoder nimbusDecoder;

    @Test
    @DisplayName("Deve montar o decoder com o jwkSetUri e o validador quando tudo estiver configurado")
    void shouldBuildDecoderWhenAllPropertiesAreConfigured() {
        try (MockedStatic<NimbusJwtDecoder> nimbus = mockStatic(NimbusJwtDecoder.class)) {
            nimbus.when(() -> NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)).thenReturn(builder);
            given(builder.build()).willReturn(nimbusDecoder);

            var decoder = KeycloakJwtDecoders.fromJwkSetUri(JWK_SET_URI, ISSUER, AUDIENCE);

            assertThat(decoder).isSameAs(nimbusDecoder);
            then(nimbusDecoder).should().setJwtValidator(any());
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("Deve lançar IllegalArgumentException quando o jwkSetUri for vazio")
    void shouldThrowIllegalArgumentWhenJwkSetUriIsBlank(String jwkSetUri) {
        assertThatThrownBy(() -> KeycloakJwtDecoders.fromJwkSetUri(jwkSetUri, ISSUER, AUDIENCE))
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
        try (MockedStatic<NimbusJwtDecoder> nimbus = mockStatic(NimbusJwtDecoder.class)) {
            nimbus.when(() -> NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)).thenReturn(builder);
            given(builder.build()).willReturn(nimbusDecoder);

            assertThatThrownBy(() -> KeycloakJwtDecoders.fromJwkSetUri(JWK_SET_URI, issuerUri, AUDIENCE))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("issuer-uri");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    @DisplayName("Deve lançar IllegalArgumentException quando a audience for vazia")
    void shouldThrowIllegalArgumentWhenAudienceIsBlank(String audience) {
        assertThatThrownBy(() -> KeycloakJwtDecoders.validator(ISSUER, audience))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("audience");
        try (MockedStatic<NimbusJwtDecoder> nimbus = mockStatic(NimbusJwtDecoder.class)) {
            nimbus.when(() -> NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)).thenReturn(builder);
            given(builder.build()).willReturn(nimbusDecoder);

            assertThatThrownBy(() -> KeycloakJwtDecoders.fromJwkSetUri(JWK_SET_URI, ISSUER, audience))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("audience");
        }
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
