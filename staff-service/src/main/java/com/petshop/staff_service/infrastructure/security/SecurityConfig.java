package com.petshop.staff_service.infrastructure.security;

import com.petshop.commons.feign.CorrelationIdFeignConfig;
import com.petshop.commons.feign.TokenPropagation;
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

/**
 * Resource Server (SDD-024): valida o access token do Keycloak via JWKS (iss, exp,
 * aud = staff-service). Chamadas de saída (customer-management) usam o client
 * {@code staff-service} do Keycloak (Client Credentials).
 */
@Configuration
@Import({CorrelationIdFeignConfig.class, KeycloakResourceServerConfig.class})
public class SecurityConfig {

    @Bean
    public TokenPropagation tokenPropagation(@Value("${keycloak.client.token-uri}") String tokenUri,
                                             @Value("${keycloak.client.id}") String clientId,
                                             @Value("${keycloak.client.secret}") String clientSecret) {
        return TokenPropagation.clientCredentials(tokenUri, clientId, clientSecret);
    }

    @Bean
    public ProblemDetailAccessDeniedHandler accessDeniedHandler() {
        return new ProblemDetailAccessDeniedHandler();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        return CorsSupport.defaultConfigurationSource("http://localhost:5173");
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                           KeycloakJwtAuthenticationConverter jwtAuthenticationConverter,
                                           ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
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
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // /availability/** é chamado internamente pelo booking-service, que
                        // repassa o token de quem originou a requisição quando há um (relay-
                        // com-fallback) — por isso aceita o usuário autenticado ou o token de
                        // serviço do booking-service com a permissão específica (SDD-024 F4).
                        .requestMatchers(HttpMethod.GET, "/api/v1/availability/**")
                            .access(PetshopAuthorization.anyUserOr(ServicePermissions.STAFF_AVAILABILITY_READ))
                        .requestMatchers("/api/v1/weekend-allocation/**").hasRole("ADMIN")
                        .anyRequest().access(PetshopAuthorization.anyUser()))
                .addFilterBefore(new CorrelationIdFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
