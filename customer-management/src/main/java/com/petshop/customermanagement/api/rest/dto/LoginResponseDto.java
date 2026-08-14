package com.petshop.customermanagement.api.rest.dto;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;

public record LoginResponseDto(
        String accessToken,
        String tokenType,
        long expiresIn,
        Role role,
        AccountType type,
        boolean mustChangePassword
) {
    public LoginResponseDto(String accessToken, long expiresIn, Role role, AccountType type, boolean mustChangePassword) {
        this(accessToken, "Bearer", expiresIn, role, type, mustChangePassword);
    }
}
