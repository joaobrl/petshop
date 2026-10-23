package com.petshop.staff_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtAuthenticationFilter;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.commons.web.CorrelationIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

@Configuration
public class SecurityConfig {

    @Bean
    public JwtService jwtService(@Value("${jwt.secret}") String secret,
                                  @Value("${jwt.expiration-minutes:15}") long expirationMinutes) {
        return new JwtService(secret, Duration.ofMinutes(expirationMinutes));
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // /availability/** só é chamado internamente pelo booking-service
                        // (relay desativado lá, sempre token de serviço) — nunca por um
                        // cliente com o próprio token, mesmo que ele conheça a URL.
                        .requestMatchers("/api/v1/availability/**").hasRole("SERVICE")
                        .requestMatchers("/api/v1/weekend-allocation/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new CorrelationIdFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }
}
