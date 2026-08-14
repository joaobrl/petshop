package com.petshop.customermanagement.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitingFilterTest {

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
        filter = new RateLimitingFilter();
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
}
