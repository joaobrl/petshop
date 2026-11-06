package com.petshop.order_service.support;

import com.petshop.commons.security.oauth2.KeycloakJwtDecoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * JwtDecoder dos testes (SDD-024 RF4): confere a chave de teste do {@link TestJwtService}
 * em vez de buscar o JWKS do Keycloak, com os validadores de produção
 * ({@link KeycloakJwtDecoders#validator}: iss, exp, aud de src/test/resources).
 *
 * <p>{@code @Configuration} comum (não {@code @TestConfiguration}) de propósito: fica no
 * pacote da aplicação em src/test e é achada pelo component scan de todo
 * {@code @SpringBootTest}; {@code @Primary} faz o SecurityConfig recebê-la no lugar do
 * decoder de produção.
 */
@Configuration
public class TestJwtDecoderConfig {

    @Bean
    @Primary
    public JwtDecoder testJwtDecoder(@Value("${keycloak.issuer-uri}") String issuerUri,
                                     @Value("${keycloak.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TestJwtService.publicKey()).build();
        decoder.setJwtValidator(KeycloakJwtDecoders.validator(issuerUri, audience));
        return decoder;
    }
}
