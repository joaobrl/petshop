package com.petshop.commons.security.jwt;

import com.petshop.commons.security.Role;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String VALID_SECRET = "test-secret-key-for-jwt-signing-min-32-bytes-long";

    @Test
    void constructorRejectsNullSecret() {
        assertThatThrownBy(() -> new JwtService(null, Duration.ofMinutes(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET não configurado.");
    }

    @Test
    void constructorRejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtService("   ", Duration.ofMinutes(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET não configurado.");
    }

    @Test
    void constructorRejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> new JwtService("muito-curto", Duration.ofMinutes(60)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET precisa ter pelo menos 32 bytes (HS256).");
    }

    @Test
    void constructorAcceptsValidSecret() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(60));

        assertThat(service).isNotNull();
        assertThat(service.expirationSeconds()).isEqualTo(3600L);
    }

    @Test
    void generateTokenAndParseRoundTrip() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(60));
        var id = UUID.randomUUID();

        String token = service.generateToken(id, "joao@petshop.com", "João", "12345678900", "11999999999",
                Role.ADMIN, AccountType.STAFF);
        AuthenticatedUser parsed = service.parse(token);

        assertThat(parsed.id()).isEqualTo(id);
        assertThat(parsed.email()).isEqualTo("joao@petshop.com");
        assertThat(parsed.name()).isEqualTo("João");
        assertThat(parsed.cpf()).isEqualTo("12345678900");
        assertThat(parsed.phone()).isEqualTo("11999999999");
        assertThat(parsed.role()).isEqualTo(Role.ADMIN);
        assertThat(parsed.type()).isEqualTo(AccountType.STAFF);
    }

    @Test
    void generateServiceTokenUsesServiceRoleAndDeterministicSubject() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(60));

        String token = service.generateServiceToken("order-service");
        AuthenticatedUser parsed = service.parse(token);

        var expectedId = UUID.nameUUIDFromBytes("order-service".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(parsed.id()).isEqualTo(expectedId);
        assertThat(parsed.email()).isEqualTo("order-service@internal");
        assertThat(parsed.role()).isEqualTo(Role.SERVICE);
        assertThat(parsed.type()).isEqualTo(AccountType.SERVICE);
    }

    @Test
    void generateServiceTokenIsDeterministicAcrossCalls() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(60));

        AuthenticatedUser first = service.parse(service.generateServiceToken("notification-service"));
        AuthenticatedUser second = service.parse(service.generateServiceToken("notification-service"));

        assertThat(first.id()).isEqualTo(second.id());
    }

    @Test
    void parseRejectsMalformedToken() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(60));

        assertThatThrownBy(() -> service.parse("isso-nao-e-um-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseRejectsTokenSignedWithDifferentKey() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(60));
        var otherService = new JwtService("outra-chave-secreta-com-pelo-menos-32-bytes!", Duration.ofMinutes(60));

        String token = otherService.generateToken(UUID.randomUUID(), "x@x.com", "X", "1", "2", Role.CUSTOMER, AccountType.CUSTOMER);

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void parseRejectsExpiredToken() {
        var service = new JwtService(VALID_SECRET, Duration.ofSeconds(-1));

        String token = service.generateToken(UUID.randomUUID(), "x@x.com", "X", "1", "2", Role.CUSTOMER, AccountType.CUSTOMER);

        assertThatThrownBy(() -> service.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void expirationSecondsReflectsConfiguredDuration() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(15));

        assertThat(service.expirationSeconds()).isEqualTo(900L);
    }

    @Test
    void keysHmacShaKeyForIsUsedImplicitlyByValidSecret() {
        // Sanity check: secret >= 32 bytes UTF-8 gera SecretKey HS256 válida sem lançar.
        assertThat(Keys.hmacShaKeyFor(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8))).isNotNull();
    }
}
