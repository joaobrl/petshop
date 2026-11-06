package com.petshop.commons.security.oauth2;

import com.petshop.commons.security.ProblemDetailAuthenticationEntryPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;

@ExtendWith(MockitoExtension.class)
class KeycloakResourceServerConfigTest {

    private static final String JWK_SET_URI = "http://localhost:8180/realms/petshop/protocol/openid-connect/certs";

    @Mock
    private NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder;
    @Mock
    private NimbusJwtDecoder nimbusDecoder;

    private final KeycloakResourceServerConfig config = new KeycloakResourceServerConfig();

    @Test
    @DisplayName("Deve criar os três beans do Resource Server")
    void shouldCreateTheThreeResourceServerBeans() {
        try (MockedStatic<NimbusJwtDecoder> nimbus = mockStatic(NimbusJwtDecoder.class)) {
            nimbus.when(() -> NimbusJwtDecoder.withJwkSetUri(JWK_SET_URI)).thenReturn(builder);
            given(builder.build()).willReturn(nimbusDecoder);

            JwtDecoder decoder = config.jwtDecoder(JWK_SET_URI, "http://localhost:8180/realms/petshop", "order-service");
            KeycloakJwtAuthenticationConverter converter = config.keycloakJwtAuthenticationConverter();
            ProblemDetailAuthenticationEntryPoint entryPoint = config.problemDetailAuthenticationEntryPoint();

            assertThat(decoder).isSameAs(nimbusDecoder);
            assertThat(converter).isNotNull();
            assertThat(entryPoint).isNotNull();
        }
    }
}
