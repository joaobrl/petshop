package com.petshop.commons.feign;

import com.petshop.commons.security.jwt.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TokenPropagationTest {

    private static final JwtService JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    private final TokenPropagation tokenPropagation = new TokenPropagation(JWT_SERVICE, "test-service");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolveAuthorizationHeaderRelaysTheTokenOfTheAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", "relayed-token", AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        var header = tokenPropagation.resolveAuthorizationHeader();

        assertThat(header).isEqualTo("Bearer relayed-token");
    }

    @Test
    void resolveAuthorizationHeaderFallsBackToServiceTokenWhenContextIsEmpty() {
        var header = tokenPropagation.resolveAuthorizationHeader();

        assertThat(header).startsWith("Bearer ");
        var parsed = JWT_SERVICE.parse(header.substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }

    @Test
    void resolveAuthorizationHeaderFallsBackToServiceTokenWhenContextIsAnonymous() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        var header = tokenPropagation.resolveAuthorizationHeader();

        var parsed = JWT_SERVICE.parse(header.substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }

    @Test
    void mintServiceTokenAlwaysGeneratesAFreshServiceToken() {
        var header = tokenPropagation.mintServiceToken();

        assertThat(header).startsWith("Bearer ");
        var parsed = JWT_SERVICE.parse(header.substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }

    @Test
    @DisplayName("Deve usar token de serviço quando a autenticação não estiver autenticada")
    void shouldFallBackToServiceTokenWhenAuthenticationIsNotAuthenticated() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", "relayed-token"));

        var header = tokenPropagation.resolveAuthorizationHeader();

        var parsed = JWT_SERVICE.parse(header.substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }

    @Test
    @DisplayName("Deve usar token de serviço quando as credentials forem nulas")
    void shouldFallBackToServiceTokenWhenCredentialsAreNull() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", null, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        var header = tokenPropagation.resolveAuthorizationHeader();

        var parsed = JWT_SERVICE.parse(header.substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }

    @Test
    @DisplayName("Deve usar token de serviço quando as credentials estiverem em branco")
    void shouldFallBackToServiceTokenWhenCredentialsAreBlank() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "user", "  ", AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        var header = tokenPropagation.resolveAuthorizationHeader();

        assertThat(header).isNotEqualTo("Bearer   ");
        var parsed = JWT_SERVICE.parse(header.substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }
}
