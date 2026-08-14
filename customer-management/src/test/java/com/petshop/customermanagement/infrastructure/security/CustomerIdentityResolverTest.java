package com.petshop.customermanagement.infrastructure.security;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sem Keycloak, o id do dono já vem direto no claim sub do token (quem
 * emitiu foi o próprio customer-management) — não precisa mais buscar
 * ninguém no banco por cpf, então o resolver não tem mais dependências.
 */
class CustomerIdentityResolverTest {

    private final CustomerIdentityResolver resolver = new CustomerIdentityResolver();

    private AuthenticatedUser customer(UUID id) {
        return new AuthenticatedUser(id, "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER);
    }

    private AuthenticatedUser staff(Role role) {
        return new AuthenticatedUser(UUID.randomUUID(), "func@petshop.local", "Func", "00000000000", "11000000000", role, AccountType.STAFF);
    }

    @Nested
    class NoRestrictionCases {

        @Test
        void doesNothingWhenUserIsNull() {
            assertThatCode(() -> resolver.requireOwnershipIfCustomer(UUID.randomUUID(), null))
                    .doesNotThrowAnyException();
        }

        @Test
        void doesNothingWhenUserIsAdmin() {
            var user = staff(Role.ADMIN);

            assertThatCode(() -> resolver.requireOwnershipIfCustomer(UUID.randomUUID(), user))
                    .doesNotThrowAnyException();
        }

        @Test
        void doesNothingWhenUserIsReceptionist() {
            var user = staff(Role.RECEPTIONIST);

            assertThatCode(() -> resolver.requireOwnershipIfCustomer(UUID.randomUUID(), user))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class CustomerOwnershipCases {

        @Test
        void passesWhenTargetIdMatchesOwnId() {
            var ownId = UUID.randomUUID();
            var user = customer(ownId);

            assertThatCode(() -> resolver.requireOwnershipIfCustomer(ownId, user))
                    .doesNotThrowAnyException();
        }

        @Test
        void throwsAccessDeniedWhenTargetIdDoesNotMatch() {
            var ownId = UUID.randomUUID();
            var otherId = UUID.randomUUID();
            var user = customer(ownId);

            assertThatThrownBy(() -> resolver.requireOwnershipIfCustomer(otherId, user))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
