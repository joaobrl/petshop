package com.petshop.customermanagement.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Throttling baseado em IP pros endpoints de autenticação sem sessão
 * (login, forgot-password) — hoje sem nenhum limite, o que permite brute
 * force / credential stuffing ilimitado. Janela fixa em memória (sem Redis):
 * suficiente porque cada serviço roda com 1 réplica hoje (ver
 * k8s/api-clientes/deployment.yaml); se isso mudar pra múltiplas réplicas,
 * o limite deixa de ser efetivo por instância e passaria a precisar de um
 * contador compartilhado.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/forgot-password"
    );

    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Window> attemptsByKey = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!LIMITED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = path + "|" + clientIp(request);
        if (isRateLimited(key)) {
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(String key) {
        var now = Instant.now();
        var window = attemptsByKey.compute(key, (k, existing) -> {
            if (existing == null || existing.expired(now)) {
                return new Window(now.plus(WINDOW));
            }
            return existing;
        });
        return window.count.incrementAndGet() > MAX_ATTEMPTS_PER_WINDOW;
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas. Aguarde um minuto antes de tentar novamente.");
        problem.setTitle("Too many requests");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }

    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private static final class Window {
        private final Instant expiresAt;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }

        private boolean expired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }
}
