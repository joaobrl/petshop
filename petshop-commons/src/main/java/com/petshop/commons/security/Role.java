package com.petshop.commons.security;

/**
 * Papéis de usuário (realm roles do Keycloak, claim {@code realm_access.roles}).
 * Chamadas serviço-a-serviço não têm papel: usam permissões específicas
 * ({@link ServicePermissions}) — o antigo papel genérico {@code SERVICE} não existe mais (SDD-024 F4).
 */
public enum Role {
    ADMIN,
    RECEPTIONIST,
    GROOMER,
    VETERINARIAN,
    CUSTOMER
}
