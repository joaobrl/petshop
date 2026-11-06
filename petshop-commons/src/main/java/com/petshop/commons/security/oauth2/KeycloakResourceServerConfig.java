package com.petshop.commons.security.oauth2;

import com.petshop.commons.security.ProblemDetailAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Beans do Resource Server comuns aos serviços (SDD-024 F2). Os serviços fazem
 * {@code @Import} desta classe e ligam os beans no próprio SecurityConfig:
 * {@code oauth2ResourceServer(o -> o.jwt(j -> j.decoder(..).jwtAuthenticationConverter(..)).authenticationEntryPoint(..))}.
 *
 * <p>Propriedades (application.yaml de cada serviço):
 * {@code keycloak.issuer-uri}, {@code keycloak.jwk-set-uri}, {@code keycloak.audience}
 * (id do próprio serviço, que precisa estar no {@code aud} do token).
 */
@Configuration
public class KeycloakResourceServerConfig {

    @Bean
    public JwtDecoder jwtDecoder(@Value("${keycloak.jwk-set-uri}") String jwkSetUri,
                                 @Value("${keycloak.issuer-uri}") String issuerUri,
                                 @Value("${keycloak.audience}") String audience) {
        return KeycloakJwtDecoders.fromJwkSetUri(jwkSetUri, issuerUri, audience);
    }

    @Bean
    public KeycloakJwtAuthenticationConverter keycloakJwtAuthenticationConverter() {
        return new KeycloakJwtAuthenticationConverter();
    }

    @Bean
    public ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint() {
        return new ProblemDetailAuthenticationEntryPoint();
    }
}
