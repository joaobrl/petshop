package com.petshop.product_registration.infrastructure.security;

import com.petshop.product_registration.infrastructure.persistence.mongo.repository.ProductMongoRepository;
import com.petshop.product_registration.infrastructure.persistence.mongo.sequence.ProductSequenceGenerator;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.product_registration.support.TestJwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
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

    private static final TestJwtService TEST_JWT_SERVICE =
            new TestJwtService();

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

    @ParameterizedTest
    @ValueSource(strings = {"reserve", "release", "confirm"})
    @DisplayName("Deve liberar reserve, release e confirm quando o token de serviço tiver svc:products:stock-write")
    void shouldAllowStockRoutesWhenServiceTokenHasStockWrite(String action) throws Exception {
        var response = send("POST", "/api/v1/products/1/" + action, TEST_JWT_SERVICE.generateServiceToken("order-service"));

        assertThat(response.statusCode()).isNotIn(401, 403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"reserve", "release", "confirm"})
    @DisplayName("Deve responder 403 em reserve, release e confirm quando o token de serviço tiver outra permissão svc")
    void shouldReturn403OnStockRoutesWhenServiceTokenHasOtherPermission(String action) throws Exception {
        var response = send("POST", "/api/v1/products/1/" + action, TEST_JWT_SERVICE.generateServiceToken("staff-service"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Deve responder 403 em rota de usuário quando o token for de serviço")
    void shouldReturn403OnUserRouteWhenTokenIsServiceToken() throws Exception {
        var response = send("POST", "/api/v1/products/register", TEST_JWT_SERVICE.generateServiceToken("order-service"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Deve responder 401 quando o aud do token for de outro serviço")
    void shouldReturn401WhenTokenAudienceIsFromAnotherService() throws Exception {
        var userToken = TEST_JWT_SERVICE.generateTokenWithAudience("order-service", UUID.randomUUID(),
                Role.ADMIN, AccountType.STAFF);
        var serviceToken = TEST_JWT_SERVICE.generateServiceTokenWithAudience("order-service", "customer-management");

        assertThat(send("DELETE", "/api/v1/products/delete/1", userToken).statusCode()).isEqualTo(401);
        assertThat(send("POST", "/api/v1/products/1/reserve", serviceToken).statusCode()).isEqualTo(401);
    }
}
