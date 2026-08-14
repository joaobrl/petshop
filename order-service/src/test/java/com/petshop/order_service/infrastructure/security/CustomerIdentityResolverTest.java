package com.petshop.order_service.infrastructure.security;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class CustomerIdentityResolverTest {

    private final CustomerIdentityResolver resolver = new CustomerIdentityResolver();

    private AuthenticatedUser customerUser(UUID id) {
        return new AuthenticatedUser(id, "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER);
    }

    private AuthenticatedUser staffUser(UUID id) {
        return new AuthenticatedUser(id, "admin@petshop.local", "Admin", "00000000000", "11000000000", Role.ADMIN, AccountType.STAFF);
    }

    @Nested
    class ResolveCustomerId {

        @Test
        void returnsRequestedIdWhenUserIsNull() {
            var requested = UUID.randomUUID();

            assertThat(resolver.resolveCustomerId(requested, null)).isEqualTo(requested);
        }

        @Test
        void returnsRequestedIdWhenUserIsNotACustomerAccount() {
            var requested = UUID.randomUUID();
            var staff = staffUser(UUID.randomUUID());

            assertThat(resolver.resolveCustomerId(requested, staff)).isEqualTo(requested);
        }

        @Test
        void returnsTokenOwnerIdWhenUserIsACustomerAccount() {
            var requested = UUID.randomUUID();
            var customerId = UUID.randomUUID();
            var customer = customerUser(customerId);

            assertThat(resolver.resolveCustomerId(requested, customer)).isEqualTo(customerId);
        }
    }

    @Nested
    class RequireOwnershipIfCustomer {

        @Test
        void doesNothingWhenUserIsNull() {
            assertThatNoException().isThrownBy(() -> resolver.requireOwnershipIfCustomer(UUID.randomUUID(), null));
        }

        @Test
        void doesNothingWhenUserIsNotACustomerAccount() {
            var staff = staffUser(UUID.randomUUID());

            assertThatNoException().isThrownBy(() -> resolver.requireOwnershipIfCustomer(UUID.randomUUID(), staff));
        }

        @Test
        void doesNothingWhenCustomerOwnsTheResource() {
            var customerId = UUID.randomUUID();
            var customer = customerUser(customerId);

            assertThatNoException().isThrownBy(() -> resolver.requireOwnershipIfCustomer(customerId, customer));
        }

        @Test
        void throwsAccessDeniedWhenCustomerDoesNotOwnTheResource() {
            var customer = customerUser(UUID.randomUUID());

            assertThatThrownBy(() -> resolver.requireOwnershipIfCustomer(UUID.randomUUID(), customer))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }
}
