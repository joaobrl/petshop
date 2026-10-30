package com.petshop.commons.web;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CorsSupportTest {

    @Test
    void registersConfigurationForAllPaths() {
        CorsConfigurationSource source = CorsSupport.defaultConfigurationSource("http://localhost:5173");

        assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);
        Map<String, CorsConfiguration> configurations = ((UrlBasedCorsConfigurationSource) source).getCorsConfigurations();
        assertThat(configurations).containsOnlyKeys("/**");
    }

    @Test
    void appliesExpectedDefaultsToAllowedOrigins() {
        CorsConfigurationSource source = CorsSupport.defaultConfigurationSource("http://localhost:5173", "http://localhost:4000");

        CorsConfiguration configuration = ((UrlBasedCorsConfigurationSource) source).getCorsConfigurations().get("/**");

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:5173", "http://localhost:4000");
        assertThat(configuration.getAllowedMethods()).containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(configuration.getAllowedHeaders()).containsExactlyInAnyOrder("Authorization", "Content-Type", "Idempotency-Key");
        assertThat(configuration.getExposedHeaders()).containsExactly("Location");
        assertThat(configuration.getAllowCredentials()).isFalse();
        assertThat(configuration.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void supportsNoAllowedOrigins() {
        CorsConfigurationSource source = CorsSupport.defaultConfigurationSource();

        CorsConfiguration configuration = ((UrlBasedCorsConfigurationSource) source).getCorsConfigurations().get("/**");

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins()).isEmpty();
    }
}
