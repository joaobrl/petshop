package com.petshop.commons.security.jwt;

import com.petshop.commons.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @ParameterizedTest(name = "{0} + emailVerified={1} -> {2}")
    @MethodSource("unverifiedCustomerCases")
    @DisplayName("Deve identificar cliente não verificado somente quando for CUSTOMER com emailVerified false")
    void shouldIdentifyUnverifiedCustomerOnlyWhenCustomerWithEmailVerifiedFalse(AccountType type, Boolean emailVerified, boolean expected) {
        var user = new AuthenticatedUser(UUID.randomUUID(), "x@mail.com", "X", "1", "2", Role.CUSTOMER, type, emailVerified);

        assertThat(user.isUnverifiedCustomer()).isEqualTo(expected);
    }

    static Stream<Arguments> unverifiedCustomerCases() {
        return Stream.of(
                Arguments.of(AccountType.CUSTOMER, false, true),
                Arguments.of(AccountType.CUSTOMER, true, false),
                Arguments.of(AccountType.CUSTOMER, null, false),
                Arguments.of(AccountType.STAFF, false, false),
                Arguments.of(AccountType.SERVICE, false, false));
    }

    @Test
    @DisplayName("Deve deixar emailVerified nulo quando usar o construtor de 7 argumentos")
    void shouldLeaveEmailVerifiedNullWhenUsingSevenArgsConstructor() {
        var user = new AuthenticatedUser(UUID.randomUUID(), "x@mail.com", "X", "1", "2", Role.CUSTOMER, AccountType.CUSTOMER);

        assertThat(user.emailVerified()).isNull();
        assertThat(user.isUnverifiedCustomer()).isFalse();
    }

    @Test
    @DisplayName("Deve ler email_verified das claims quando fromClaims receber valor booleano")
    void shouldReadEmailVerifiedFromClaimsWhenValueIsBoolean() {
        var claims = claims(false);

        var user = AuthenticatedUser.fromClaims(claims);

        assertThat(user.emailVerified()).isFalse();
        assertThat(user.isUnverifiedCustomer()).isTrue();
    }

    @Test
    @DisplayName("Deve deixar emailVerified nulo quando as claims não tiverem email_verified")
    void shouldLeaveEmailVerifiedNullWhenClaimIsAbsent() {
        var claims = claims(null);
        claims.remove("email_verified");

        var user = AuthenticatedUser.fromClaims(claims);

        assertThat(user.emailVerified()).isNull();
    }

    @Test
    @DisplayName("Deve lançar exceção quando email_verified não for booleano")
    void shouldThrowWhenEmailVerifiedClaimIsNotBoolean() {
        var claims = claims("false");

        assertThatThrownBy(() -> AuthenticatedUser.fromClaims(claims))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Claim 'email_verified' não é booleana.");
    }

    private static Map<String, Object> claims(Object emailVerified) {
        var claims = new HashMap<String, Object>();
        claims.put("sub", UUID.randomUUID().toString());
        claims.put("email", "x@mail.com");
        claims.put("role", Role.CUSTOMER.name());
        claims.put("type", AccountType.CUSTOMER.name());
        claims.put("email_verified", emailVerified);
        return claims;
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
