package com.petshop.order_service.infrastructure.security;

import com.petshop.commons.feign.TokenPropagation;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class SecurityConfigTest {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    @DisplayName("Deve montar o registro com client_secret_basic e o token-uri informado em clientCredentials")
    void shouldBuildRegistrationWithClientSecretBasicAndTokenUriWhenClientCredentials() throws Exception {
        var authorizationHeader = new AtomicReference<String>();
        var requestBody = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/token", exchange -> {
            authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var response = "{\"access_token\":\"issued-token\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var tokenUri = "http://127.0.0.1:" + server.getAddress().getPort() + "/token";

            TokenPropagation propagation = securityConfig.tokenPropagation(tokenUri, "order-service", "s3cret");
            var header = propagation.mintServiceToken();

            var expectedBasic = "Basic " + Base64.getEncoder()
                    .encodeToString("order-service:s3cret".getBytes(StandardCharsets.UTF_8));
            assertThat(header).isEqualTo("Bearer issued-token");
            assertThat(authorizationHeader.get()).isEqualTo(expectedBasic);
            assertThat(requestBody.get()).contains("grant_type=client_credentials");
        } finally {
            server.stop(0);
        }
    }
}
