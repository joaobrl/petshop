package com.petshop.customermanagement.core.port.out.dto;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Staff;

import java.util.UUID;

/**
 * Dados de um usuário no provedor de identidade (Keycloak, SDD-024 F3). {@code userId} é o
 * UUID local (claim {@code user_id}); o resto vira os claims do access token.
 */
public record IdentityUser(UUID userId, String email, String name, String cpf, String phone,
                           Role role, AccountType type, boolean emailVerified) {

    public static IdentityUser of(Customer customer) {
        return new IdentityUser(customer.getId(), customer.getEmail(), customer.getName(), customer.getCpf(),
                customer.getPhone(), Role.CUSTOMER, AccountType.CUSTOMER, customer.hasVerifiedEmail());
    }

    // Staff não passa pela verificação de e-mail da SDD-020 (cadastrado pelo ADMIN).
    public static IdentityUser of(Staff staff) {
        return new IdentityUser(staff.getId(), staff.getEmail(), staff.getName(), staff.getCpf(),
                staff.getPhone(), staff.getRole(), AccountType.STAFF, true);
    }
}
