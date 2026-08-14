package com.petshop.product_registration.infrastructure.security;

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

/**
 * JWT próprio (substitui o Keycloak). product-registration não emite
 * token — só valida (o segredo tem que ser o mesmo em todos os
 * serviços). Regras inalteradas: catálogo (list/find) público,
 * reserve/release/confirm autenticado (chamado via Feign pelo
 * order-service), register/update/delete só ADMIN.
 */
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
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests(auth -> auth
                        // Sem isso, o probe de liveness/readiness do k8s (sem token) cai
                        // no anyRequest().authenticated() e toma 401 pra sempre, deixando
                        // o pod fora de Ready. Só health é exposto por padrão, então isso
                        // não vaza mais nada.
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/products/list", "/api/v1/products/find/**").permitAll()
                        .requestMatchers("/api/v1/products/*/reserve", "/api/v1/products/*/release", "/api/v1/products/*/confirm").authenticated()
                        .requestMatchers("/api/v1/products/register", "/api/v1/products/update/**", "/api/v1/products/delete/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new CorrelationIdFilter(), JwtAuthenticationFilter.class);
        return http.build();
    }
}
