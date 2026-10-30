package com.petshop.commons.web;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Shared CORS setup for local development (the React frontend in {@code web/}
 * runs on a different origin/port than every backend module). Each module's
 * {@code SecurityConfig} wires this into its filter chain via
 * {@code http.cors(Customizer.withDefaults())} plus a
 * {@code CorsConfigurationSource} bean built from {@link #defaultConfigurationSource}.
 */
public final class CorsSupport {

    private CorsSupport() {
    }

    public static CorsConfigurationSource defaultConfigurationSource(String... allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Idempotency-Key: checkout do order-service (repetir a chamada devolve o
        // mesmo pedido em vez de criar outro).
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
