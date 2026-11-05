package com.petshop.booking_service.support;

import com.petshop.commons.feign.TokenPropagation;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.time.Instant;
import java.util.UUID;

/**
 * TokenPropagation dos testes (SDD-024 F4): o Client Credentials do Keycloak é simulado
 * por um OAuth2AuthorizedClientManager que devolve sempre {@link #serviceToken(String)}.
 */
public final class TestTokenPropagation {

    public static final AuthenticatedUser USER = new AuthenticatedUser(UUID.randomUUID(), "user@petshop.com", "User",
            "12345678900", "11999990000", Role.CUSTOMER, AccountType.CUSTOMER);

    private TestTokenPropagation() {
    }

    /** Valor do access token que o "Keycloak" devolve ao client dado. */
    public static String serviceToken(String clientId) {
        return "client-credentials-token-" + clientId;
    }

    public static TokenPropagation forService(String clientId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                .clientId(clientId)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost:8180/realms/petshop/protocol/openid-connect/token")
                .build();
        return new TokenPropagation(request -> new OAuth2AuthorizedClient(registration, clientId,
                new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, serviceToken(clientId),
                        Instant.now(), Instant.now().plusSeconds(300))), "keycloak");
    }

    /** Simula o Resource Server com um token de usuário válido: principal AuthenticatedUser, token bruto como credentials. */
    public static void authenticateUser(String rawToken) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                USER, rawToken, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));
    }
}
