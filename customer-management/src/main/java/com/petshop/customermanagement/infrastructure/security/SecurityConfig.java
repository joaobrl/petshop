package com.petshop.customermanagement.infrastructure.security;

import com.petshop.commons.security.jwt.JwtAuthenticationFilter;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.commons.web.CorrelationIdFilter;
import com.petshop.commons.web.CorsSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.web.cors.CorsConfigurationSource;

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
    public CorsConfigurationSource corsConfigurationSource() {
        return CorsSupport.defaultConfigurationSource("http://localhost:5173");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/forgot-password").permitAll()
                        .requestMatchers("/api/v1/auth/change-password").authenticated()
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
