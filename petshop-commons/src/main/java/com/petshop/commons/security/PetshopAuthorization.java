package com.petshop.commons.security;

import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;

import java.util.Arrays;

/**
 * Regras de autorização reutilizadas pelos SecurityConfig (SDD-024 F4).
 *
 * <p>Token de service account só tem permissões {@code svc:*}, nunca {@code ROLE_*}. Por
 * isso as rotas de usuário usam {@link #anyUser()} em vez de {@code authenticated()}:
 * um token de serviço válido (assinatura/iss/aud) recebe 403 em toda rota que não
 * libere explicitamente a permissão dele.
 */
public final class PetshopAuthorization {

    private static final String[] USER_ROLES = Arrays.stream(Role.values()).map(Enum::name).toArray(String[]::new);

    private PetshopAuthorization() {
    }

    /** Qualquer usuário com um dos papéis do projeto. */
    public static <T> AuthorizationManager<T> anyUser() {
        return AuthorityAuthorizationManager.hasAnyRole(USER_ROLES);
    }

    /** Qualquer usuário, ou o serviço com a permissão dada (rota interna que aceita relay do token do usuário). */
    public static <T> AuthorizationManager<T> anyUserOr(String servicePermission) {
        return AuthorizationManagers.anyOf(anyUser(), AuthorityAuthorizationManager.hasAuthority(servicePermission));
    }

    /** Só os papéis dados, ou o serviço com a permissão dada. */
    public static <T> AuthorizationManager<T> rolesOr(String servicePermission, String... roles) {
        return AuthorizationManagers.anyOf(AuthorityAuthorizationManager.hasAnyRole(roles),
                AuthorityAuthorizationManager.hasAuthority(servicePermission));
    }
}
