package com.petshop.staff_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtService;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TokenPropagationTest {

    private static final JwtService JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    private final TokenPropagation tokenPropagation = new TokenPropagation(JWT_SERVICE);

    @Test
    void mintServiceTokenAlwaysGeneratesAFreshServiceToken() {
        var header = tokenPropagation.mintServiceToken();

        assertThat(header).startsWith("Bearer ");
        var parsed = JWT_SERVICE.parse(header.substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }
}
