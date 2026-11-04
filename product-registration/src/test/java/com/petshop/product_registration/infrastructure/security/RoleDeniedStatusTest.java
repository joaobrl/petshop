package com.petshop.product_registration.infrastructure.security;

import com.petshop.product_registration.infrastructure.persistence.mongo.repository.ProductMongoRepository;
import com.petshop.product_registration.infrastructure.persistence.mongo.sequence.ProductSequenceGenerator;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class RoleDeniedStatusTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    private static final JwtService TEST_JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private String token(Role role) {
        return TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "user@petshop.com", "User", "12345678900",
                "11999990000", role, AccountType.STAFF);
    }

    private HttpResponse<String> send(String method, String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @MockitoBean
    private ProductMongoRepository productRepository;
    @MockitoBean
    private ProductSequenceGenerator sequenceGenerator;

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER", "RECEPTIONIST"})
    @DisplayName("Deve responder 403 problem+json para papel sem permissão na rota restrita")
    void shouldReturn403ProblemJsonWhenRoleLacksPermission(Role role) throws Exception {
        var response = send("DELETE", "/api/v1/products/delete/1", token(role));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(response.body())
                .contains("\"title\":\"Acesso negado\"")
                .contains("\"status\":403")
                .contains("\"instance\":\"/api/v1/products/delete/1\"");
    }

    @Test
    @DisplayName("Deve responder 401 quando a rota restrita for chamada sem token")
    void shouldReturn401WhenNoTokenOnRestrictedRoute() throws Exception {
        var response = send("DELETE", "/api/v1/products/delete/1", null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Deve responder 401 quando o token for inválido")
    void shouldReturn401WhenTokenIsInvalid() throws Exception {
        var response = send("DELETE", "/api/v1/products/delete/1", "token-invalido");

        assertThat(response.statusCode()).isEqualTo(401);
    }
}
