package com.petshop.order_service.infrastructure.security;

import com.petshop.commons.security.ProblemDetailAccessDeniedHandler;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.order_service.support.TestJwtService;
import com.petshop.order_service.core.port.out.CustomerPortOut;
import com.petshop.order_service.core.port.out.NotificationPortOut;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import com.petshop.order_service.core.port.out.PurchaseHistoryPortOut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoleDeniedStatusTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ProblemDetailAccessDeniedHandler accessDeniedHandler;

    private final HttpClient http = HttpClient.newHttpClient();

    @MockitoBean
    private ProductStockPortOut productStockPortOut;
    @MockitoBean
    private CustomerPortOut customerPortOut;
    @MockitoBean
    private NotificationPortOut notificationPortOut;
    @MockitoBean
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    @Test
    @DisplayName("Deve responder 401 quando uma rota autenticada for chamada sem token")
    void shouldReturn401WhenNoTokenOnAuthenticatedRoute() throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/orders/1")).GET().build();

        var response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Deve registrar o handler de acesso negado no contexto")
    void shouldRegisterAccessDeniedHandlerBean() {
        assertThat(accessDeniedHandler).isNotNull();
    }

    private static final TestJwtService TEST_JWT_SERVICE = new TestJwtService();

    private HttpResponse<String> get(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    @DisplayName("Deve responder 403 em rota de usuário quando o token for de serviço")
    void shouldReturn403OnUserRouteWhenTokenIsServiceToken() throws Exception {
        var response = get("/api/v1/orders/1", TEST_JWT_SERVICE.generateServiceToken("booking-service"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Deve responder 401 quando o aud do token for de outro serviço")
    void shouldReturn401WhenTokenAudienceIsFromAnotherService() throws Exception {
        var userToken = TEST_JWT_SERVICE.generateTokenWithAudience("customer-management", UUID.randomUUID(),
                Role.ADMIN, AccountType.STAFF);
        var serviceToken = TEST_JWT_SERVICE.generateServiceTokenWithAudience("booking-service", "customer-management");

        assertThat(get("/api/v1/orders/1", userToken).statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/orders/1", serviceToken).statusCode()).isEqualTo(401);
    }
}
