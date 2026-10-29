package com.petshop.customermanagement.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
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
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/forgot-password"
    );

    private static final int MAX_ATTEMPTS_PER_WINDOW = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    // Teto duro contra ataque distribuído com IPs rotativos: acima disso o
    // Caffeine despeja janelas em vez de estourar a memória.
    private static final long MAX_TRACKED_KEYS = 100_000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ClientIpResolver clientIpResolver;
    // expireAfterWrite: a janela começa na 1ª tentativa e a entrada some
    // quando ela vence (incrementar o contador não conta como escrita).
    private final Cache<String, AtomicInteger> attemptsByKey;

    public RateLimitingFilter(ClientIpResolver clientIpResolver) {
        this(clientIpResolver, Ticker.systemTicker());
    }

    RateLimitingFilter(ClientIpResolver clientIpResolver, Ticker ticker) {
        this.clientIpResolver = clientIpResolver;
        this.attemptsByKey = Caffeine.newBuilder()
                .expireAfterWrite(WINDOW)
                .maximumSize(MAX_TRACKED_KEYS)
                .ticker(ticker)
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!LIMITED_PATHS.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = path + "|" + clientIpResolver.resolve(request);
        if (isRateLimited(key)) {
            writeTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isRateLimited(String key) {
        var attempts = attemptsByKey.get(key, k -> new AtomicInteger(0));
        return attempts.incrementAndGet() > MAX_ATTEMPTS_PER_WINDOW;
    }

    long activeWindows() {
        attemptsByKey.cleanUp();
        return attemptsByKey.estimatedSize();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas. Aguarde um minuto antes de tentar novamente.");
        problem.setTitle("Too many requests");

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
