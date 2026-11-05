package com.petshop.commons.feign;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TokenPropagationTest {

    // Token que o Keycloak (simulado) devolve no client_credentials (SDD-024 F4).
    private static final String SERVICE_HEADER = "Bearer service-token";

    private static final AuthenticatedUser USER = new AuthenticatedUser(UUID.randomUUID(), "user@petshop.com", "User",
            "12345678900", "11999990000", Role.CUSTOMER, AccountType.CUSTOMER);

    private static final ClientRegistration REGISTRATION = ClientRegistration.withRegistrationId("keycloak")
            .clientId("test-service")
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri("http://localhost:8180/realms/petshop/protocol/openid-connect/token")
            .build();

    private final TokenPropagation tokenPropagation = new TokenPropagation(request -> new OAuth2AuthorizedClient(
            REGISTRATION, "test-service",
            new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "service-token",
                    Instant.now(), Instant.now().plusSeconds(300))), "keycloak");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveAuthorizationHeaderRelaysTheTokenOfTheAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                USER, "relayed-token", AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        var header = tokenPropagation.resolveAuthorizationHeader();

        assertThat(header).isEqualTo("Bearer relayed-token");
    }

    @Test
    void resolveAuthorizationHeaderFallsBackToServiceTokenWhenContextIsEmpty() {
        var header = tokenPropagation.resolveAuthorizationHeader();

        assertThat(header).isEqualTo(SERVICE_HEADER);
    }

    @Test
    void resolveAuthorizationHeaderFallsBackToServiceTokenWhenContextIsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        var header = tokenPropagation.resolveAuthorizationHeader();

        assertThat(header).isEqualTo(SERVICE_HEADER);
    }

    @Test
    void mintServiceTokenAlwaysGeneratesAFreshServiceToken() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                USER, "relayed-token", AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        var header = tokenPropagation.mintServiceToken();

        assertThat(header).isEqualTo(SERVICE_HEADER);
    }

    @Test
    @DisplayName("Deve usar token de serviço quando a autenticação não estiver autenticada")
    void shouldFallBackToServiceTokenWhenAuthenticationIsNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER, "relayed-token"));

        var header = tokenPropagation.resolveAuthorizationHeader();

        assertThat(header).isEqualTo(SERVICE_HEADER);
    }

    @Test
    @DisplayName("Deve usar token de serviço quando as credentials forem nulas")
    void shouldFallBackToServiceTokenWhenCredentialsAreNull() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                USER, null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        var header = tokenPropagation.resolveAuthorizationHeader();

        assertThat(header).isEqualTo(SERVICE_HEADER);
    }

    @Test
    @DisplayName("Deve usar token de serviço quando as credentials estiverem em branco")
    void shouldFallBackToServiceTokenWhenCredentialsAreBlank() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                USER, "  ", AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        var header = tokenPropagation.resolveAuthorizationHeader();

        assertThat(header).isEqualTo(SERVICE_HEADER);
    }
}
