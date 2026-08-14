package com.petshop.staff_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtService;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class FeignTokenRelayConfigTest {

    private static final JwtService JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    private final FeignTokenRelayConfig config = new FeignTokenRelayConfig(JWT_SERVICE);

    @Mock
    private HttpServletRequest servletRequest;

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void usesServiceTokenWhenNoRequestAttributesBound() {
        RequestContextHolder.resetRequestAttributes();
        var requestTemplate = new RequestTemplate();

        config.tokenRelayInterceptor().apply(requestTemplate);

        assertIsServiceToken(requestTemplate);
    }

    @Test
    void usesServiceTokenEvenWhenAnUserTokenIsPresent() {
        // Sem stub do header Authorization de propósito: o tokenRelayInterceptor nunca lê
        // o header do request original (ver javadoc de FeignTokenRelayConfig) — só a
        // presença de um ServletRequestAttributes vinculado já basta pra provar isso.
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));
        var requestTemplate = new RequestTemplate();

        config.tokenRelayInterceptor().apply(requestTemplate);

        assertIsServiceToken(requestTemplate);
    }

    private void assertIsServiceToken(RequestTemplate requestTemplate) {
        var header = requestTemplate.headers().get("Authorization").iterator().next();
        assertThat(header).startsWith("Bearer ");
        var parsed = JWT_SERVICE.parse(header.substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }
}
