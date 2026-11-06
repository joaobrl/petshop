package com.petshop.commons.security.oauth2;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class KeycloakJwtAuthenticationConverterTest {

    private static final String RAW_TOKEN = "raw.jwt.token";

    private final KeycloakJwtAuthenticationConverter converter = new KeycloakJwtAuthenticationConverter();

    private static Jwt jwt(Map<String, Object> claims) {
        return Jwt.withTokenValue(RAW_TOKEN)
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .claims(c -> c.putAll(claims))
                .build();
    }

    private static Map<String, Object> userClaims(UUID id, List<String> roles) {
        var claims = new HashMap<String, Object>();
        claims.put("user_id", id.toString());
        claims.put("email", "maria@mail.com");
        claims.put("type", AccountType.CUSTOMER.name());
        claims.put("realm_access", Map.of("roles", roles));
        return claims;
    }

    private static Map<String, Object> serviceClaims(String azp, List<String> roles) {
        var claims = new HashMap<String, Object>();
        claims.put("sub", "service-account-subject");
        claims.put("realm_access", Map.of("roles", roles));
        if (azp != null) {
            claims.put("azp", azp);
        }
        return claims;
    }

    @Test
    @DisplayName("Deve montar AuthenticatedUser com ROLE_<papel> quando o token tiver user_id")
    void shouldBuildAuthenticatedUserWithRoleAuthorityWhenTokenHasUserId() {
        var userId = UUID.randomUUID();

        var authentication = converter.convert(jwt(userClaims(userId, List.of("offline_access", "CUSTOMER"))));

        assertThat(authentication.getPrincipal()).isInstanceOf(AuthenticatedUser.class);
        var user = (AuthenticatedUser) authentication.getPrincipal();
        assertThat(user.id()).isEqualTo(userId);
        assertThat(user.role()).isEqualTo(Role.CUSTOMER);
        assertThat(authentication.getName()).isEqualTo(userId.toString());
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_CUSTOMER");
        assertThat(authentication.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("Deve ficar sem authorities quando o usuário não tiver papel conhecido em realm_access")
    void shouldHaveNoAuthoritiesWhenUserHasNoKnownRole() {
        var authentication = converter.convert(jwt(userClaims(UUID.randomUUID(), List.of("offline_access", "uma_authorization"))));

        assertThat(authentication.getAuthorities()).isEmpty();
        assertThat(((AuthenticatedUser) authentication.getPrincipal()).role()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidUserClaims")
    @DisplayName("Deve lançar InvalidBearerTokenException quando as claims de usuário forem inválidas")
    void shouldThrowInvalidBearerTokenWhenUserClaimsAreInvalid(String description, String claim, Object value) {
        var claims = userClaims(UUID.randomUUID(), List.of("CUSTOMER"));
        if (value == null) {
            claims.remove(claim);
        } else {
            claims.put(claim, value);
        }

        assertThatThrownBy(() -> converter.convert(jwt(claims)))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    static Stream<Arguments> invalidUserClaims() {
        return Stream.of(
                Arguments.of("sem type", "type", null),
                Arguments.of("email não-texto", "email", 123),
                Arguments.of("user_id não-texto", "user_id", 42),
                Arguments.of("type desconhecido", "type", "ALIEN"),
                Arguments.of("user_id não é UUID", "user_id", "abc"));
    }

    @Test
    @DisplayName("Deve montar ServiceAccount só com authorities svc quando não houver user_id e houver azp")
    void shouldBuildServiceAccountWithOnlySvcAuthoritiesWhenNoUserIdAndAzpPresent() {
        var authentication = converter.convert(jwt(serviceClaims("order-service",
                List.of("svc:customers:customer-read", "ADMIN", "offline_access", "svc:products:stock-write"))));

        assertThat(authentication.getPrincipal()).isEqualTo(new ServiceAccount("order-service"));
        assertThat(authentication.getName()).isEqualTo("order-service");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("svc:customers:customer-read", "svc:products:stock-write");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("Deve lançar InvalidBearerTokenException quando não houver user_id e o azp for nulo ou em branco")
    void shouldThrowInvalidBearerTokenWhenNoUserIdAndAzpIsNullOrBlank(String azp) {
        var claims = serviceClaims(azp, List.of("svc:customers:customer-read"));

        assertThatThrownBy(() -> converter.convert(jwt(claims)))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("user_id");
    }

    @Test
    @DisplayName("Deve expor o token bruto como credentials da autenticação")
    void shouldExposeRawTokenAsCredentials() {
        var userAuthentication = converter.convert(jwt(userClaims(UUID.randomUUID(), List.of("ADMIN"))));
        var serviceAuthentication = converter.convert(jwt(serviceClaims("staff-service", List.of())));

        assertThat(userAuthentication.getCredentials()).isEqualTo(RAW_TOKEN);
        assertThat(serviceAuthentication.getCredentials()).isEqualTo(RAW_TOKEN);
    }
}
