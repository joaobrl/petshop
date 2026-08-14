package com.petshop.order_service.infrastructure.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecurityConfig monta dois beans: JwtService (testado aqui) e filterChain,
 * que depende de HttpSecurity real e é coberto pelos testes de integração
 * MockMvc.
 */
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void jwtServiceBeanBuildsWithAValidSecret() {
        var jwtService = securityConfig.jwtService("a-valid-secret-with-at-least-32-bytes", 60);

        assertThat(jwtService).isNotNull();
        assertThat(jwtService.expirationSeconds()).isEqualTo(3600L);
    }

    @Test
    void jwtServiceBeanRejectsATooShortSecret() {
        assertThatThrownBy(() -> securityConfig.jwtService("too-short", 60))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jwtServiceBeanRejectsABlankSecret() {
        assertThatThrownBy(() -> securityConfig.jwtService(" ", 60))
                .isInstanceOf(IllegalStateException.class);
    }
}
