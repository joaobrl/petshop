package com.petshop.commons.feign;

import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Resolve o header Authorization a repassar nas chamadas Feign de saída (SDD-024 F4).
 * {@code resolveAuthorizationHeader} repassa o token do usuário autenticado na
 * requisição atual (já validado pelo Resource Server), senão um token de serviço
 * (relay-com-fallback); {@code mintServiceToken} sempre usa o token de serviço (rotas
 * internas que não podem aceitar o token de um cliente, ou serviços que só fazem
 * consulta de sistema).
 *
 * <p>Token de serviço = Client Credentials no Keycloak, com o client do próprio serviço
 * (um por serviço chamador). O {@link OAuth2AuthorizedClientManager} guarda o token e só
 * pede outro quando ele está para expirar (cache até expirar). Nenhum serviço assina token.
 */
public class TokenPropagation {

    static final String REGISTRATION_ID = "keycloak";
    private static final String BEARER_PREFIX = "Bearer ";

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final String registrationId;

    public TokenPropagation(OAuth2AuthorizedClientManager authorizedClientManager, String registrationId) {
        this.authorizedClientManager = authorizedClientManager;
        this.registrationId = registrationId;
    }

    /**
     * Client Credentials no token endpoint do Keycloak ({@code keycloak.client.*} de cada
     * serviço chamador). Funciona fora de requisição HTTP (schedulers).
     */
    public static TokenPropagation clientCredentials(String tokenUri, String clientId, String clientSecret) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri(tokenUri)
                .build();
        var registrations = new InMemoryClientRegistrationRepository(registration);
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
        return new TokenPropagation(manager, REGISTRATION_ID);
    }

    /**
     * Repassa o token guardado como credentials pelo Resource Server quando o principal é
     * um usuário. Sem usuário (anônimo em rota pública, token de outro serviço, scheduler),
     * usa o token de serviço. Nunca repassa string não verificada.
     */
    public String resolveAuthorizationHeader() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        // AnonymousAuthenticationToken (rota pública sem token) também é isAuthenticated(),
        // mas o principal não é AuthenticatedUser.
        if (auth != null
                && auth.isAuthenticated()
                && auth.getPrincipal() instanceof AuthenticatedUser
                && auth.getCredentials() instanceof String raw
                && !raw.isBlank()) {
            return BEARER_PREFIX + raw;
        }
        return mintServiceToken();
    }

    public String mintServiceToken() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(registrationId)
                .principal(registrationId)
                .build();
        OAuth2AuthorizedClient client = authorizedClientManager.authorize(request);
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("Keycloak não devolveu token de serviço (client_credentials).");
        }
        return BEARER_PREFIX + client.getAccessToken().getTokenValue();
    }
}
