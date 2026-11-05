package com.petshop.commons.security.jwt;

import com.petshop.commons.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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

    // parse() e generateServiceToken() saíram na SDD-024 (F2/F4): nenhum serviço valida
    // HS256 nem assina token de serviço. Só a emissão legada do login fica, conferida
    // aqui direto com o jjwt.
    @Test
    void generateTokenSignsLegacyClaims() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(60));
        var id = UUID.randomUUID();

        String token = service.generateToken(id, "joao@petshop.com", "João", "12345678900", "11999999999",
                Role.ADMIN, AccountType.STAFF);
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(VALID_SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(id.toString());
        assertThat(claims.get("email")).isEqualTo("joao@petshop.com");
        assertThat(claims.get("name")).isEqualTo("João");
        assertThat(claims.get("cpf")).isEqualTo("12345678900");
        assertThat(claims.get("phone")).isEqualTo("11999999999");
        assertThat(claims.get("role")).isEqualTo("ADMIN");
        assertThat(claims.get("type")).isEqualTo("STAFF");
    }

    private static Claims claimsOf(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(VALID_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    @DisplayName("Deve incluir a claim email_verified true no token")
    void shouldIncludeEmailVerifiedClaimTrue() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(60));

        String token = service.generateToken(UUID.randomUUID(), "maria@mail.com", "Maria", "12345678900", "11999999999",
                Role.CUSTOMER, AccountType.CUSTOMER, true);

        assertThat(claimsOf(token).get("email_verified")).isEqualTo(true);
    }

    @Test
    @DisplayName("Deve incluir a claim email_verified false no token")
    void shouldIncludeEmailVerifiedClaimFalse() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(60));

        String token = service.generateToken(UUID.randomUUID(), "maria@mail.com", "Maria", "12345678900", "11999999999",
                Role.CUSTOMER, AccountType.CUSTOMER, false);

        assertThat(claimsOf(token).get("email_verified")).isEqualTo(false);
    }

    @Test
    @DisplayName("Deve omitir a claim email_verified quando o valor for nulo")
    void shouldOmitEmailVerifiedClaimWhenValueIsNull() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(60));

        String token = service.generateToken(UUID.randomUUID(), "maria@mail.com", "Maria", "12345678900", "11999999999",
                Role.CUSTOMER, AccountType.CUSTOMER, null);

        assertThat(claimsOf(token).containsKey("email_verified")).isFalse();
    }

    @Test
    @DisplayName("Deve omitir a claim email_verified quando usar a versão sem o parâmetro")
    void shouldOmitEmailVerifiedClaimWhenUsingOverloadWithoutParameter() {
        var service = new JwtService(VALID_SECRET, Duration.ofMinutes(60));

        String token = service.generateToken(UUID.randomUUID(), "joao@petshop.com", "João", "12345678900", "11999999999",
                Role.ADMIN, AccountType.STAFF);

        assertThat(claimsOf(token).containsKey("email_verified")).isFalse();
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
