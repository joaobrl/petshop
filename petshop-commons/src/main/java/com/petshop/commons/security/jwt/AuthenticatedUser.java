package com.petshop.commons.security.jwt;

import com.petshop.commons.security.Role;

import java.util.Map;
import java.util.UUID;

/**
 * Identidade autenticada da requisição. É o {@code principal} que o
 * {@link JwtAuthenticationFilter} coloca no {@code SecurityContext}; os controllers
 * a recebem via {@code @AuthenticationPrincipal} em vez de reparsear o header.
 *
 * <p>{@link #fromClaims(Map)} recebe só o mapa de claims, para que a origem possa
 * mudar (ex.: {@code Jwt#getClaims()} do Resource Server) sem tocar nos controllers.
 */
public record AuthenticatedUser(UUID id, String email, String name, String cpf, String phone, Role role, AccountType type) {

    public static final String CLAIM_SUBJECT = "sub";
    public static final String CLAIM_EMAIL = "email";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_CPF = "cpf";
    public static final String CLAIM_PHONE = "phone";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "type";

    public static AuthenticatedUser fromClaims(Map<String, ?> claims) {
        return new AuthenticatedUser(
                UUID.fromString(stringClaim(claims, CLAIM_SUBJECT)),
                stringClaim(claims, CLAIM_EMAIL),
                stringClaim(claims, CLAIM_NAME),
                stringClaim(claims, CLAIM_CPF),
                stringClaim(claims, CLAIM_PHONE),
                Role.valueOf(stringClaim(claims, CLAIM_ROLE)),
                AccountType.valueOf(stringClaim(claims, CLAIM_TYPE))
        );
    }

    private static String stringClaim(Map<String, ?> claims, String name) {
        Object value = claims.get(name);
        if (value == null || value instanceof String) {
            return (String) value;
        }
        throw new IllegalArgumentException("Claim '" + name + "' não é texto.");
    }
}
