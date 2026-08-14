package com.petshop.commons.security.jwt;

import com.petshop.commons.security.Role;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email, String name, String cpf, String phone, Role role, AccountType type) {
}
