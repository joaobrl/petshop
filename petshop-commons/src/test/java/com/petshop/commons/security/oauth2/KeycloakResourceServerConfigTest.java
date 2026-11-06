package com.petshop.commons.security.oauth2;

import com.petshop.commons.security.ProblemDetailAuthenticationEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakResourceServerConfigTest {

    private static final String JWK_SET_URI = "http://localhost:8180/realms/petshop/protocol/openid-connect/certs";

    private final KeycloakResourceServerConfig config = new KeycloakResourceServerConfig();

    @Test
    @DisplayName("Deve criar os três beans do Resource Server")
    void shouldCreateTheThreeResourceServerBeans() {
        JwtDecoder decoder = config.jwtDecoder(JWK_SET_URI, "http://localhost:8180/realms/petshop", "order-service");
        KeycloakJwtAuthenticationConverter converter = config.keycloakJwtAuthenticationConverter();
        ProblemDetailAuthenticationEntryPoint entryPoint = config.problemDetailAuthenticationEntryPoint();

        assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
        assertThat(converter).isNotNull();
        assertThat(entryPoint).isNotNull();
    }
}
