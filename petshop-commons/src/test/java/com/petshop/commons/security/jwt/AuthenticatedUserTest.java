package com.petshop.commons.security.jwt;

import com.petshop.commons.security.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.List;
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
        claims.put("user_id", UUID.randomUUID().toString());
        claims.put("email", "x@mail.com");
        claims.put("realm_access", Map.of("roles", List.of(Role.CUSTOMER.name())));
        claims.put("type", AccountType.CUSTOMER.name());
        claims.put("email_verified", emailVerified);
        return claims;
    }

    @Test
    @DisplayName("Deve devolver lista vazia em realmRoles quando não houver realm_access")
    void shouldReturnEmptyRealmRolesWhenRealmAccessIsAbsent() {
        assertThat(AuthenticatedUser.realmRoles(Map.of("user_id", "x"))).isEmpty();
    }

    @Test
    @DisplayName("Deve devolver lista vazia em realmRoles quando roles não for uma lista")
    void shouldReturnEmptyRealmRolesWhenRolesIsNotACollection() {
        var claims = Map.<String, Object>of("realm_access", Map.of("roles", "ADMIN"));

        assertThat(AuthenticatedUser.realmRoles(claims)).isEmpty();
    }

    @Test
    @DisplayName("Deve devolver lista vazia em realmRoles quando realm_access não for um mapa")
    void shouldReturnEmptyRealmRolesWhenRealmAccessIsNotAMap() {
        var claims = Map.<String, Object>of("realm_access", "ADMIN");

        assertThat(AuthenticatedUser.realmRoles(claims)).isEmpty();
    }

    @Test
    @DisplayName("Deve ignorar em realmRoles os itens que não forem texto")
    void shouldIgnoreNonTextItemsInRealmRoles() {
        var claims = Map.<String, Object>of("realm_access", Map.of("roles", List.of("ADMIN", 7, "svc:a")));

        assertThat(AuthenticatedUser.realmRoles(claims)).containsExactly("ADMIN", "svc:a");
    }

    @Test
    @DisplayName("Deve deixar o papel nulo quando nenhum papel de realm_access for conhecido")
    void shouldLeaveRoleNullWhenNoRealmRoleIsKnown() {
        var claims = claims(false);
        claims.put("realm_access", Map.of("roles", List.of("offline_access")));

        var user = AuthenticatedUser.fromClaims(claims);

        assertThat(user.role()).isNull();
    }

    @Test
    @DisplayName("Deve escolher o primeiro papel na ordem do enum quando houver vários papéis conhecidos")
    void shouldPickFirstRoleInEnumOrderWhenSeveralRolesAreKnown() {
        var claims = claims(false);
        claims.put("realm_access", Map.of("roles", List.of("CUSTOMER", "ADMIN")));

        var user = AuthenticatedUser.fromClaims(claims);

        assertThat(user.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Deve lançar exceção quando as claims não tiverem user_id")
    void shouldThrowWhenUserIdClaimIsMissing() {
        var claims = claims(false);
        claims.remove("user_id");

        assertThatThrownBy(() -> AuthenticatedUser.fromClaims(claims))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Claims 'user_id' e 'type' são obrigatórios.");
    }

    @Test
    @DisplayName("Deve lançar exceção quando as claims não tiverem type")
    void shouldThrowWhenTypeClaimIsMissing() {
        var claims = claims(false);
        claims.remove("type");

        assertThatThrownBy(() -> AuthenticatedUser.fromClaims(claims))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Claims 'user_id' e 'type' são obrigatórios.");
    }

    @Test
    @DisplayName("Deve lançar exceção quando uma claim de texto não for texto")
    void shouldThrowWhenTextClaimIsNotText() {
        var claims = claims(false);
        claims.put("email", 123);

        assertThatThrownBy(() -> AuthenticatedUser.fromClaims(claims))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Claim 'email' não é texto.");
    }

    @Test
    void supportsNullNameCpfPhone() {
        var id = UUID.randomUUID();

        var user = new AuthenticatedUser(id, "maria@mail.com", null, null, null,
                Role.CUSTOMER, AccountType.CUSTOMER);

        assertThat(user.name()).isNull();
        assertThat(user.cpf()).isNull();
        assertThat(user.phone()).isNull();
        assertThat(user.role()).isEqualTo(Role.CUSTOMER);
        assertThat(user.type()).isEqualTo(AccountType.CUSTOMER);
    }
}
