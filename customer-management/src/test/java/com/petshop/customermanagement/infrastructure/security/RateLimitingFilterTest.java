package com.petshop.customermanagement.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.github.benmanes.caffeine.cache.Ticker;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

    private static final String TRUSTED_PROXIES = "172.16.0.0/12";
    private static final String GATEWAY_IP = "172.18.0.5";
    private static final String LOGIN = "/api/v1/auth/login";

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private RateLimitingFilter filter;
    private StringWriter responseBody;

    @BeforeEach
    void setUp() throws Exception {
        filter = new RateLimitingFilter(new ClientIpResolver(List.of(TRUSTED_PROXIES)));
        responseBody = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(responseBody));
    }

    @Test
    void allowsPathsOutsideTheLimitedSetThroughUnconditionally() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/change-password");

        for (int i = 0; i < 20; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(20)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void allowsUpToFiveAttemptsPerMinutePerIpOnLogin() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        verify(filterChain, times(5)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void blocksTheSixthAttemptFromTheSameIpWithinTheWindow() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");

        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(5)).doFilter(request, response);
        verify(response).setStatus(429);
        assertThat(responseBody.toString()).contains("Muitas tentativas");
    }

    @Test
    void tracksDifferentIpsIndependently() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");

        when(request.getRemoteAddr()).thenReturn("10.0.0.3");
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        when(request.getRemoteAddr()).thenReturn("10.0.0.4");
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(6)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void tracksLoginAndForgotPasswordIndependentlyForTheSameIp() throws Exception {
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");

        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        for (int i = 0; i < 5; i++) {
            filter.doFilterInternal(request, response, filterChain);
        }

        when(request.getRequestURI()).thenReturn("/api/v1/auth/forgot-password");
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(6)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    // CA1: atrás do gateway, cada cliente real tem a própria janela.
    @Test
    void behindTrustedGatewayLimitsPerForwardedClientIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(attempt(filter, GATEWAY_IP, "1.1.1.1").getStatus()).isEqualTo(200);
        }

        var sixth = attempt(filter, GATEWAY_IP, "1.1.1.1");
        var otherClient = attempt(filter, GATEWAY_IP, "2.2.2.2");

        assertThat(sixth.getStatus()).isEqualTo(429);
        assertThat(sixth.getContentType()).isEqualTo("application/problem+json");
        assertThat(sixth.getContentAsString())
                .contains("\"title\":\"Too many requests\"")
                .contains("Muitas tentativas. Aguarde um minuto antes de tentar novamente.");
        assertThat(otherClient.getStatus()).isEqualTo(200);
    }

    // CA2: XFF forjado (variando a cada chamada) por cliente não confiável
    // não abre janelas novas — tudo conta para o remoteAddr.
    @Test
    void forgedXForwardedForFromUntrustedClientCountsForRemoteAddr() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(attempt(filter, "203.0.113.7", "9.9.9." + i).getStatus()).isEqualTo(200);
        }

        assertThat(attempt(filter, "203.0.113.7", "9.9.9.99").getStatus()).isEqualTo(429);
        assertThat(filter.activeWindows()).isEqualTo(1);
    }

    // CA4: chamada direta ao serviço (sem gateway, sem XFF) segue por remoteAddr.
    @Test
    void directCallWithoutGatewayIsLimitedByRemoteAddr() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(attempt(filter, "203.0.113.8").getStatus()).isEqualTo(200);
        }

        assertThat(attempt(filter, "203.0.113.8").getStatus()).isEqualTo(429);
        assertThat(attempt(filter, "203.0.113.9").getStatus()).isEqualTo(200);
    }

    // CA3: janelas vencidas saem do cache — tamanho segue os IPs ativos no
    // último minuto, não o histórico.
    @Test
    void expiredWindowsAreEvictedFromTheCache() throws Exception {
        var nanos = new AtomicLong();
        Ticker ticker = nanos::get;
        var timedFilter = new RateLimitingFilter(new ClientIpResolver(List.of(TRUSTED_PROXIES)), ticker);

        for (int i = 0; i < 1000; i++) {
            attempt(timedFilter, GATEWAY_IP, "10.%d.%d.1".formatted(i / 256, i % 256));
        }
        assertThat(timedFilter.activeWindows()).isEqualTo(1000);

        nanos.addAndGet(Duration.ofMinutes(1).plusSeconds(1).toNanos());

        assertThat(timedFilter.activeWindows()).isZero();
    }

    @Test
    void blockedClientIsAllowedAgainAfterTheWindowExpires() throws Exception {
        var nanos = new AtomicLong();
        Ticker ticker = nanos::get;
        var timedFilter = new RateLimitingFilter(new ClientIpResolver(List.of(TRUSTED_PROXIES)), ticker);

        for (int i = 0; i < 5; i++) {
            attempt(timedFilter, GATEWAY_IP, "1.1.1.1");
        }
        assertThat(attempt(timedFilter, GATEWAY_IP, "1.1.1.1").getStatus()).isEqualTo(429);

        nanos.addAndGet(Duration.ofMinutes(1).plusSeconds(1).toNanos());

        assertThat(attempt(timedFilter, GATEWAY_IP, "1.1.1.1").getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse attempt(RateLimitingFilter target, String remoteAddr,
                                                   String... xForwardedFor) throws Exception {
        var request = new MockHttpServletRequest("POST", LOGIN);
        request.setRemoteAddr(remoteAddr);
        for (var value : xForwardedFor) {
            request.addHeader(ClientIpResolver.X_FORWARDED_FOR, value);
        }
        var response = new MockHttpServletResponse();
        target.doFilterInternal(request, response, new MockFilterChain());
        return response;
    }
}
