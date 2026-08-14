package com.petshop.order_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtService;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class FeignAuthConfigTest {

    private static final JwtService JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    private final FeignAuthConfig feignAuthConfig = new FeignAuthConfig(JWT_SERVICE);

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void relaysTheIncomingAuthorizationHeaderWhenThereIsAnHttpRequestInProgress() {
        var request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer relayed-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var template = new RequestTemplate();
        feignAuthConfig.feignAuthInterceptor().apply(template);

        assertThat(template.headers().get("Authorization")).containsExactly("Bearer relayed-token");
    }

    @Test
    void fallsBackToServiceTokenWhenIncomingAuthorizationHeaderIsAbsent() {
        var request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var template = new RequestTemplate();
        feignAuthConfig.feignAuthInterceptor().apply(template);

        var header = template.headers().get("Authorization").iterator().next();
        assertThat(header).startsWith("Bearer ");
        var parsed = JWT_SERVICE.parse(header.substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }

    @Test
    void fallsBackToServiceTokenWhenIncomingAuthorizationHeaderIsBlank() {
        var request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("   ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        var template = new RequestTemplate();
        feignAuthConfig.feignAuthInterceptor().apply(template);

        assertThat(template.headers().get("Authorization").iterator().next()).startsWith("Bearer ");
    }

    @Test
    void fallsBackToServiceTokenWhenThereIsNoHttpRequestInProgress() {
        RequestContextHolder.resetRequestAttributes();

        var template = new RequestTemplate();
        feignAuthConfig.feignAuthInterceptor().apply(template);

        var header = template.headers().get("Authorization").iterator().next();
        assertThat(header).startsWith("Bearer ");
        var parsed = JWT_SERVICE.parse(header.substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }
}
