package com.petshop.product_registration.infrastructure.security;

import com.petshop.commons.security.PetshopAuthorization;
import com.petshop.commons.security.ProblemDetailAccessDeniedHandler;
import com.petshop.commons.security.ProblemDetailAuthenticationEntryPoint;
import com.petshop.commons.security.ServicePermissions;
import com.petshop.commons.security.oauth2.KeycloakJwtAuthenticationConverter;
import com.petshop.commons.security.oauth2.KeycloakResourceServerConfig;
import com.petshop.commons.web.CorrelationIdFilter;
import com.petshop.commons.web.CorsSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Resource Server (SDD-024): só valida o access token do Keycloak via JWKS (iss, exp,
 * aud = product-registration) — não emite token. Regras: catálogo (list/find) público,
 * reserve/release/confirm para usuário ou para o order-service com
 * {@link ServicePermissions#PRODUCTS_STOCK_WRITE} (o order-service repassa o token de
 * quem originou a requisição quando há um, senão usa o próprio token de serviço — ver
 * TokenPropagation), register/update/delete só ADMIN.
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
    public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                           KeycloakJwtAuthenticationConverter jwtAuthenticationConverter,
                                           ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
                                           ProblemDetailAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler::handle)
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler::handle))
                .authorizeHttpRequests(auth -> auth
                        // Sem isso, o probe de liveness/readiness do k8s (sem token) cai
                        // no anyRequest() e toma 401 pra sempre, deixando
                        // o pod fora de Ready. Só health é exposto por padrão, então isso
                        // não vaza mais nada.
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/products/list", "/api/v1/products/find", "/api/v1/products/find/**").permitAll()
                        .requestMatchers("/api/v1/products/*/reserve", "/api/v1/products/*/release", "/api/v1/products/*/confirm")
                            .access(PetshopAuthorization.anyUserOr(ServicePermissions.PRODUCTS_STOCK_WRITE))
                        .requestMatchers("/api/v1/products/register", "/api/v1/products/update/**", "/api/v1/products/delete/**").hasRole("ADMIN")
                        .anyRequest().access(PetshopAuthorization.anyUser()))
                .addFilterBefore(new CorrelationIdFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}
