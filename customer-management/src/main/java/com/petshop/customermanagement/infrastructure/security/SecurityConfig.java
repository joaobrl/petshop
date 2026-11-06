package com.petshop.customermanagement.infrastructure.security;

import com.petshop.commons.security.PetshopAuthorization;
import com.petshop.commons.security.ProblemDetailAccessDeniedHandler;
import com.petshop.commons.security.ProblemDetailAuthenticationEntryPoint;
import com.petshop.commons.security.ServicePermissions;
import com.petshop.commons.security.oauth2.KeycloakJwtAuthenticationConverter;
import com.petshop.commons.security.oauth2.KeycloakResourceServerConfig;
import com.petshop.commons.web.CorrelationIdFilter;
import com.petshop.commons.web.CorsSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

/**
 * Resource Server (SDD-024): valida o access token do Keycloak via JWKS (iss, exp,
 * aud = customer-management). Chamadores internos: order-service
 * ({@link ServicePermissions#CUSTOMERS_CUSTOMER_READ}) e staff-service
 * ({@link ServicePermissions#CUSTOMERS_STAFF_READ}).
 */
@Configuration
@Import(KeycloakResourceServerConfig.class)
public class SecurityConfig {

    @Bean
    public ProblemDetailAccessDeniedHandler accessDeniedHandler() {
        return new ProblemDetailAccessDeniedHandler();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return CorsSupport.defaultConfigurationSource("http://localhost:5173");
    }

    @Bean
    public ClientIpResolver clientIpResolver(@Value("${rate-limit.trusted-proxies:}") List<String> trustedProxies) {
        return new ClientIpResolver(trustedProxies);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                           KeycloakJwtAuthenticationConverter jwtAuthenticationConverter,
                                           ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                           ClientIpResolver clientIpResolver,
                                           ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler::handle))
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler::handle))
                // Rotas de usuário usam anyUser() em vez de authenticated(): token de serviço
                // só passa onde a permissão dele é liberada explicitamente (403 no resto).
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/verify-email").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/resend-verification").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/staff/all")
                            .access(PetshopAuthorization.rolesOr(ServicePermissions.CUSTOMERS_STAFF_READ, "ADMIN"))
                        .requestMatchers("/api/v1/staff/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/customers/register/customer").permitAll()
                        .requestMatchers("/api/v1/customers/list/customers").hasAnyRole("ADMIN", "RECEPTIONIST")
                        .requestMatchers("/api/v1/customers/list/pets").hasAnyRole("ADMIN", "RECEPTIONIST")
                        .requestMatchers(HttpMethod.GET, "/api/v1/customers/find/customer/**")
                            .access(PetshopAuthorization.anyUserOr(ServicePermissions.CUSTOMERS_CUSTOMER_READ))
                        .requestMatchers("/api/v1/customers/find/customer/**").access(PetshopAuthorization.anyUser())
                        .requestMatchers("/api/v1/customers/update/customer/**").access(PetshopAuthorization.anyUser())
                        .requestMatchers("/api/v1/customers/delete/customer/**").access(PetshopAuthorization.anyUser())
                        .requestMatchers("/api/v1/customers/reactivate/customer/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/customers/register/*/pet").access(PetshopAuthorization.anyUser())
                        .requestMatchers("/api/v1/customers/*/pets/**").access(PetshopAuthorization.anyUser())
                        .requestMatchers("/api/v1/history/bookings").hasAnyRole("ADMIN", "RECEPTIONIST")
                        .requestMatchers("/api/v1/history/purchases").hasAnyRole("ADMIN", "RECEPTIONIST")
                        .requestMatchers("/api/v1/history/bookings/*").access(PetshopAuthorization.anyUser())
                        .requestMatchers("/api/v1/history/purchases/*").access(PetshopAuthorization.anyUser())
                        .anyRequest().hasAnyRole("ADMIN", "RECEPTIONIST"))
                .addFilterBefore(new RateLimitingFilter(clientIpResolver), BearerTokenAuthenticationFilter.class)
                .addFilterBefore(new CorrelationIdFilter(), RateLimitingFilter.class);
        return http.build();
    }
}
