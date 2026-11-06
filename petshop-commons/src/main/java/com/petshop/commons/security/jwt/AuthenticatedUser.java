package com.petshop.commons.security.jwt;

import com.petshop.commons.security.Role;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Identidade autenticada de um usuário (cliente ou funcionário). É o {@code principal}
 * que o {@code KeycloakJwtAuthenticationConverter} coloca no {@code SecurityContext};
 * os controllers a recebem via {@code @AuthenticationPrincipal} em vez de reparsear o header.
 *
 * <p>{@link #fromClaims(Map)} lê os claims do access token do Keycloak (SDD-024):
 * {@code user_id} (UUID do customer-management, não o {@code sub} do Keycloak),
 * {@code email}, {@code name}, {@code cpf}, {@code phone}, {@code type},
 * {@code email_verified} e o papel em {@code realm_access.roles}. Token de service
 * account não tem {@code user_id} e não vira {@code AuthenticatedUser} (ver
 * {@code ServiceAccount}).
 *
 * <p>{@code emailVerified} (SDD-020) vem da claim padrão {@value #CLAIM_EMAIL_VERIFIED}
 * (escopo {@code email} do Keycloak) e é {@code null} quando ela não existe. Claim
 * ausente nunca bloqueia ninguém, ver {@link #isUnverifiedCustomer()}.
 */
public record AuthenticatedUser(UUID id, String email, String name, String cpf, String phone, Role role, AccountType type,
                                Boolean emailVerified) {

    public static final String CLAIM_USER_ID = "user_id";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_CPF = "cpf";
    public static final String CLAIM_PHONE = "phone";
    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_EMAIL_VERIFIED = "email_verified";
    public static final String CLAIM_REALM_ACCESS = "realm_access";
    public static final String CLAIM_ROLES = "roles";

    /** Identidade sem a claim {@value #CLAIM_EMAIL_VERIFIED}. */
    public AuthenticatedUser(UUID id, String email, String name, String cpf, String phone, Role role, AccountType type) {
        this(id, email, name, cpf, phone, role, type, null);
    }

    /**
     * Cliente cujo token diz explicitamente {@code email_verified=false}. Claim
     * ausente não conta como "não verificado"; staff nunca é bloqueado.
     */
    public boolean isUnverifiedCustomer() {
        return type == AccountType.CUSTOMER && Boolean.FALSE.equals(emailVerified);
    }

    /**
     * @throws IllegalArgumentException se {@code user_id} ou {@code type} faltarem ou
     *         forem inválidos (fail-closed: sem {@code type} a checagem de ownership
     *         de cliente seria pulada)
     */
    public static AuthenticatedUser fromClaims(Map<String, ?> claims) {
        String userId = stringClaim(claims, CLAIM_USER_ID);
        String type = stringClaim(claims, CLAIM_TYPE);
        if (userId == null || type == null) {
            throw new IllegalArgumentException("Claims 'user_id' e 'type' são obrigatórios.");
        }
        return new AuthenticatedUser(
                UUID.fromString(userId),
                stringClaim(claims, CLAIM_EMAIL),
                stringClaim(claims, CLAIM_NAME),
                stringClaim(claims, CLAIM_CPF),
                stringClaim(claims, CLAIM_PHONE),
                roleFrom(realmRoles(claims)),
                AccountType.valueOf(type),
                booleanClaim(claims, CLAIM_EMAIL_VERIFIED)
        );
    }

    /** Papéis de {@code realm_access.roles}; vazio se o claim não existir. */
    public static List<String> realmRoles(Map<String, ?> claims) {
        if (claims.get(CLAIM_REALM_ACCESS) instanceof Map<?, ?> realmAccess
                && realmAccess.get(CLAIM_ROLES) instanceof Collection<?> roles) {
            return roles.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    /**
     * Primeiro papel do projeto presente na lista, na ordem do enum (ADMIN antes de
     * CUSTOMER); {@code null} se o token não tiver nenhum (sem acesso a rota de usuário).
     */
    private static Role roleFrom(List<String> realmRoles) {
        for (Role role : Role.values()) {
            if (realmRoles.contains(role.name())) {
                return role;
            }
        }
        return null;
    }

    private static String stringClaim(Map<String, ?> claims, String name) {
        Object value = claims.get(name);
        if (value == null || value instanceof String) {
            return (String) value;
        }
        throw new IllegalArgumentException("Claim '" + name + "' não é texto.");
    }

    private static Boolean booleanClaim(Map<String, ?> claims, String name) {
        Object value = claims.get(name);
        if (value == null || value instanceof Boolean) {
            return (Boolean) value;
        }
        throw new IllegalArgumentException("Claim '" + name + "' não é booleana.");
    }
}
