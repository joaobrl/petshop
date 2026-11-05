package com.petshop.commons.security.oauth2;

/**
 * Principal de um token de service account do Keycloak (Client Credentials, SDD-024 F4):
 * sem {@code user_id}, identificado pelo client que o pediu ({@code azp}).
 * {@code @AuthenticationPrincipal AuthenticatedUser} recebe {@code null} nesse caso.
 */
public record ServiceAccount(String clientId) {
}
