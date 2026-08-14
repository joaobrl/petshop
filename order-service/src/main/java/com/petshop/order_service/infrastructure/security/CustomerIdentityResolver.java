package com.petshop.order_service.infrastructure.security;

import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CustomerIdentityResolver {

    public UUID resolveCustomerId(UUID requestedCustomerId, AuthenticatedUser user) {
        if (user == null || user.type() != AccountType.CUSTOMER) {
            return requestedCustomerId;
        }
        return user.id();
    }

    public void requireOwnershipIfCustomer(UUID resourceCustomerId, AuthenticatedUser user) {
        if (user == null || user.type() != AccountType.CUSTOMER) {
            return;
        }
        if (!user.id().equals(resourceCustomerId)) {
            throw new AccessDeniedException("Cliente só pode acessar os próprios pedidos/carrinho.");
        }
    }
}
