package com.petshop.commons.security.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class KeycloakAuthenticationTokenTest {

    private final Jwt jwt = Jwt.withTokenValue("raw.jwt.token")
            .header("alg", "RS256")
            .subject("subject-1")
            .claim("azp", "order-service")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(300))
            .build();

    private final KeycloakAuthenticationToken authentication = new KeycloakAuthenticationToken(
            jwt, new ServiceAccount("order-service"), "order-service",
            List.of(new SimpleGrantedAuthority("svc:customers:customer-read")));

    @Test
    @DisplayName("Deve devolver o nome informado em getName")
    void shouldReturnGivenNameWhenGetName() {
        assertThat(authentication.getName()).isEqualTo("order-service");
    }

    @Test
    @DisplayName("Deve devolver as claims do jwt em getTokenAttributes")
    void shouldReturnJwtClaimsWhenGetTokenAttributes() {
        assertThat(authentication.getTokenAttributes()).isEqualTo(jwt.getClaims());
        assertThat(authentication.getTokenAttributes()).containsEntry("azp", "order-service");
    }

    @Test
    @DisplayName("Deve nascer autenticada com principal, credentials e authorities informados")
    void shouldBeAuthenticatedWithGivenPrincipalCredentialsAndAuthorities() {
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isEqualTo(new ServiceAccount("order-service"));
        assertThat(authentication.getCredentials()).isEqualTo("raw.jwt.token");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("svc:customers:customer-read");
    }
}
