package com.petshop.customermanagement.infrastructure.security;

import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CustomerIdentityResolver {

    public void requireOwnershipIfCustomer(UUID targetCustomerId, AuthenticatedUser user) {
        if (user == null || user.type() != AccountType.CUSTOMER) {
            return;
        }
        if (!user.id().equals(targetCustomerId)) {
            throw new AccessDeniedException("Cliente só pode acessar o próprio cadastro.");
        }
    }
}
