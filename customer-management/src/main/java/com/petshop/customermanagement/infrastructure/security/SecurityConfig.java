package com.petshop.customermanagement.infrastructure.security;

import com.petshop.commons.security.jwt.JwtAuthenticationFilter;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.commons.web.CorrelationIdFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

import java.time.Duration;

@Configuration
public class SecurityConfig {

    @Bean
    public JwtService jwtService(@Value("${jwt.secret}") String secret,
                                  @Value("${jwt.expiration-minutes:15}") long expirationMinutes) {
        return new JwtService(secret, Duration.ofMinutes(expirationMinutes));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Sem isso, o probe de liveness/readiness do k8s (que não manda
                        // token — é só o kubelet checando "tá de pé?") cai no
                        // anyRequest().hasAnyRole(...) e toma 401 pra sempre, e o pod
                        // nunca fica Ready. Só health é exposto por HTTP por padrão
                        // (sem management.endpoints.web.exposure.include configurado),
                        // então liberar /actuator/health/** não vaza nada além disso.
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/forgot-password").permitAll()
                        .requestMatchers("/api/v1/auth/change-password").authenticated()
                        // GET /staff/all é consumido internamente pelo staff-service (via
                        // FeignTokenRelayConfig lá, sempre com token de serviço — ver
                        // javadoc de lá) pra montar disponibilidade/escala; role SERVICE
                        // cobre isso, sem abrir criação/edição/remoção de funcionário
                        // (essas continuam só ADMIN, regra abaixo).
                        .requestMatchers(HttpMethod.GET, "/api/v1/staff/all").hasAnyRole("ADMIN", "SERVICE")
                        .requestMatchers("/api/v1/staff/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/customers/register/customer").permitAll()
                        .requestMatchers("/api/v1/customers/list/customers").hasAnyRole("ADMIN", "RECEPTIONIST")
                        .requestMatchers("/api/v1/customers/list/pets").hasAnyRole("ADMIN", "RECEPTIONIST")
                        .requestMatchers("/api/v1/customers/find/customer/**").authenticated()
                        .requestMatchers("/api/v1/customers/update/customer/**").authenticated()
                        .requestMatchers("/api/v1/customers/delete/customer/**").authenticated()
                        .requestMatchers("/api/v1/customers/register/*/pet").authenticated()
                        .requestMatchers("/api/v1/customers/*/pets/**").authenticated()
                        .requestMatchers("/api/v1/history/bookings").hasAnyRole("ADMIN", "RECEPTIONIST")
                        .requestMatchers("/api/v1/history/purchases").hasAnyRole("ADMIN", "RECEPTIONIST")
                        .requestMatchers("/api/v1/history/bookings/*").authenticated()
                        .requestMatchers("/api/v1/history/purchases/*").authenticated()
                        .anyRequest().hasAnyRole("ADMIN", "RECEPTIONIST"))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new RateLimitingFilter(), JwtAuthenticationFilter.class)
                .addFilterBefore(new CorrelationIdFilter(), RateLimitingFilter.class);
        return http.build();
    }
}
