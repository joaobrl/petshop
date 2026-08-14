package com.petshop.customermanagement.infrastructure.security;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecurityConfig hoje só monta beans (JwtService, PasswordEncoder,
 * SecurityFilterChain) — a lógica de extrair role/claims do token virou
 * o JwtAuthenticationFilter compartilhado (petshop-commons), e a matriz
 * de autorização em si já é coberta pelos testes de integração MockMvc
 * (CustomerControllerIntegrationTest/HistoryControllerIntegrationTest).
 * Aqui só garantimos que os beans que este serviço monta funcionam.
 */
class SecurityConfigTest {

    private static final String VALID_SECRET = "test-secret-key-for-jwt-signing-min-32-bytes-long";

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void jwtServiceBeanGeneratesAndParsesTokenRoundTrip() {
        JwtService jwtService = securityConfig.jwtService(VALID_SECRET, 60L);
        var customerId = UUID.randomUUID();

        var token = jwtService.generateToken(customerId, "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER);
        var user = jwtService.parse(token);

        assertThat(user.id()).isEqualTo(customerId);
        assertThat(user.email()).isEqualTo("maria@mail.com");
        assertThat(user.role()).isEqualTo(Role.CUSTOMER);
        assertThat(user.type()).isEqualTo(AccountType.CUSTOMER);
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
