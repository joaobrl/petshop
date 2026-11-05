package com.petshop.customermanagement.infrastructure.security;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecurityConfig hoje só monta beans (JwtService — só a emissão legada, que sai na
 * SDD-024 F3 —, PasswordEncoder, SecurityFilterChain) — a validação do token é o
 * Resource Server com o conversor do petshop-commons, e a matriz
 * de autorização em si já é coberta pelos testes de integração MockMvc
 * (CustomerControllerIntegrationTest/HistoryControllerIntegrationTest).
 * Aqui só garantimos que os beans que este serviço monta funcionam.
 */
class SecurityConfigTest {

    private static final String VALID_SECRET = "test-secret-key-for-jwt-signing-min-32-bytes-long";

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void jwtServiceBeanGeneratesSignedTokenWithClaims() {
        JwtService jwtService = securityConfig.jwtService(VALID_SECRET, 60L);
        var customerId = UUID.randomUUID();

        var token = jwtService.generateToken(customerId, "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER);
        // Nenhum serviço valida mais HS256 (SDD-024 F2): o teste confere a emissão direto com o jjwt.
        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(VALID_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getSubject()).isEqualTo(customerId.toString());
        assertThat(claims.get("email")).isEqualTo("maria@mail.com");
        assertThat(claims.get("role")).isEqualTo(Role.CUSTOMER.name());
        assertThat(claims.get("type")).isEqualTo(AccountType.CUSTOMER.name());
    }

    @Test
    void jwtServiceBeanRejectsSecretShorterThan32Bytes() {
        assertThatThrownBy(() -> securityConfig.jwtService("too-short", 60L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void passwordEncoderBeanEncodesAndMatches() {
        var encoder = securityConfig.passwordEncoder();

        var hash = encoder.encode("12345678900");

        assertThat(encoder.matches("12345678900", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }
}
