package com.petshop.commons.security.jwt;

import com.petshop.commons.security.Role;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedUserTest {

    @Test
    void exposesAllFieldsViaAccessors() {
        var id = UUID.randomUUID();

        var user = new AuthenticatedUser(id, "joao@petshop.com", "João", "12345678900", "11999999999",
                Role.ADMIN, AccountType.STAFF);

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.email()).isEqualTo("joao@petshop.com");
        assertThat(user.name()).isEqualTo("João");
        assertThat(user.cpf()).isEqualTo("12345678900");
        assertThat(user.phone()).isEqualTo("11999999999");
        assertThat(user.role()).isEqualTo(Role.ADMIN);
        assertThat(user.type()).isEqualTo(AccountType.STAFF);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var id = UUID.randomUUID();

        var a = new AuthenticatedUser(id, "joao@petshop.com", "João", "12345678900", "11999999999",
                Role.ADMIN, AccountType.STAFF);
        var b = new AuthenticatedUser(id, "joao@petshop.com", "João", "12345678900", "11999999999",
                Role.ADMIN, AccountType.STAFF);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("email");
    }

    @Test
    void supportsNullNameCpfPhoneForServiceAccounts() {
        var id = UUID.randomUUID();

        var user = new AuthenticatedUser(id, "order-service@internal", null, null, null,
                Role.SERVICE, AccountType.SERVICE);

        assertThat(user.name()).isNull();
        assertThat(user.cpf()).isNull();
        assertThat(user.phone()).isNull();
        assertThat(user.role()).isEqualTo(Role.SERVICE);
        assertThat(user.type()).isEqualTo(AccountType.SERVICE);
    }
}
