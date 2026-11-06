package com.petshop.commons.security.oauth2;

import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.Assert;

/**
 * Monta o JwtDecoder dos serviços (SDD-024 RF2/RF3).
 *
 * <p>Chave pública via JWKS ({@code jwk-set-uri}, rede interna, ex.:
 * {@code http://keycloak:8080/realms/petshop/protocol/openid-connect/certs}); o
 * {@code iss} esperado é o público ({@code http://localhost:8180/realms/petshop}),
 * fixado no Keycloak por {@code KC_HOSTNAME}. O NimbusJwtDecoder guarda o JWKS em cache e
 * o busca de novo quando chega um {@code kid} desconhecido: rotação de chave no Keycloak
 * não exige restart (RF3). Só aceita RS256 — token HS256 (antigo {@code JWT_SECRET}) é
 * recusado (CA1).
 */
public final class KeycloakJwtDecoders {

    private KeycloakJwtDecoders() {
    }

    public static NimbusJwtDecoder fromJwkSetUri(String jwkSetUri, String issuerUri, String audience) {
        Assert.hasText(jwkSetUri, "keycloak.jwk-set-uri não configurado.");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(validator(issuerUri, audience));
        return decoder;
    }

    /** {@code exp}/{@code nbf} (padrão), {@code iss} e {@code aud} contendo o id do serviço que recebe. */
    public static OAuth2TokenValidator<Jwt> validator(String issuerUri, String audience) {
        Assert.hasText(issuerUri, "keycloak.issuer-uri não configurado.");
        Assert.hasText(audience, "keycloak.audience não configurado.");
        return JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(issuerUri),
                new JwtAudienceValidator(audience));
    }
}
