package com.petshop.commons.feign;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.petshop.commons.security.oauth2.ServiceAccount;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class TokenPropagationTest {

    @Mock
    private OAuth2AuthorizedClientManager manager;
    @Mock
    private OAuth2AuthorizedClient authorizedClient;

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

    private TokenPropagation withMockedManager() {
        return new TokenPropagation(manager, "keycloak");
    }

    private void givenServiceTokenFromManager(String value) {
        var accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, value,
                Instant.now(), Instant.now().plusSeconds(300));
        given(authorizedClient.getAccessToken()).willReturn(accessToken);
        given(manager.authorize(any(OAuth2AuthorizeRequest.class))).willReturn(authorizedClient);
    }

    @Test
    @DisplayName("Deve repassar o token bruto sem consultar o manager quando o principal for AuthenticatedUser")
    void shouldRelayRawTokenWithoutCallingManagerWhenPrincipalIsAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                USER, "relayed-token", AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        var header = withMockedManager().resolveAuthorizationHeader();

        assertThat(header).isEqualTo("Bearer relayed-token");
        then(manager).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Deve usar token de serviço quando o principal for ServiceAccount")
    void shouldUseServiceTokenWhenPrincipalIsServiceAccount() {
        givenServiceTokenFromManager("minted-token");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new ServiceAccount("order-service"), "incoming-service-token",
                AuthorityUtils.createAuthorityList("svc:customers:customer-read")));

        var header = withMockedManager().resolveAuthorizationHeader();

        assertThat(header).isEqualTo("Bearer minted-token");
        then(manager).should().authorize(any(OAuth2AuthorizeRequest.class));
    }

    @Test
    @DisplayName("Deve usar token de serviço quando o principal não for AuthenticatedUser")
    void shouldUseServiceTokenWhenPrincipalIsNotAuthenticatedUser() {
        givenServiceTokenFromManager("minted-token");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "texto", "some-token", AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        var header = withMockedManager().resolveAuthorizationHeader();

        assertThat(header).isEqualTo("Bearer minted-token");
    }

    @Test
    @DisplayName("Deve usar token de serviço quando a autenticação for anônima")
    void shouldUseServiceTokenWhenAuthenticationIsAnonymous() {
        givenServiceTokenFromManager("minted-token");
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        var header = withMockedManager().resolveAuthorizationHeader();

        assertThat(header).isEqualTo("Bearer minted-token");
    }

    @Test
    @DisplayName("Deve usar token de serviço quando a credencial do usuário estiver em branco")
    void shouldUseServiceTokenWhenUserCredentialIsBlank() {
        givenServiceTokenFromManager("minted-token");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                USER, " ", AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        var header = withMockedManager().resolveAuthorizationHeader();

        assertThat(header).isEqualTo("Bearer minted-token");
    }

    @Test
    @DisplayName("Deve pedir o token ao manager com o registrationId keycloak ao gerar token de serviço")
    void shouldAuthorizeWithKeycloakRegistrationIdWhenMintingServiceToken() {
        givenServiceTokenFromManager("minted-token");
        var captor = ArgumentCaptor.forClass(OAuth2AuthorizeRequest.class);

        withMockedManager().mintServiceToken();

        then(manager).should().authorize(captor.capture());
        assertThat(captor.getValue().getClientRegistrationId()).isEqualTo("keycloak");
        assertThat(captor.getValue().getPrincipal().getName()).isEqualTo("keycloak");
    }

    @Test
    @DisplayName("Deve lançar IllegalStateException quando o manager devolver null")
    void shouldThrowIllegalStateWhenManagerReturnsNull() {
        given(manager.authorize(any(OAuth2AuthorizeRequest.class))).willReturn(null);

        assertThatThrownBy(() -> withMockedManager().mintServiceToken())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token de serviço");
    }

    @Test
    @DisplayName("Deve pedir o token com client_secret_basic no token-uri informado quando usar clientCredentials")
    void shouldRequestTokenWithClientSecretBasicAtTokenUriWhenUsingClientCredentials() throws Exception {
        var authorizationHeader = new AtomicReference<String>();
        var requestBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"access_token\":\"issued-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var tokenUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";

            var header = TokenPropagation.clientCredentials(tokenUri, "order-service", "s3cret").mintServiceToken();

            var expectedBasic = "Basic " + Base64.getEncoder()
                    .encodeToString("order-service:s3cret".getBytes(StandardCharsets.UTF_8));
            assertThat(header).isEqualTo("Bearer issued-token");
            assertThat(authorizationHeader.get()).isEqualTo(expectedBasic);
            assertThat(requestBody.get()).contains("grant_type=client_credentials");
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Deve lançar IllegalStateException quando o access token do client for nulo")
    void shouldThrowIllegalStateWhenAccessTokenIsNull() {
        given(authorizedClient.getAccessToken()).willReturn(null);
        given(manager.authorize(any(OAuth2AuthorizeRequest.class))).willReturn(authorizedClient);

        assertThatThrownBy(() -> withMockedManager().mintServiceToken())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("token de serviço");
    }
}
