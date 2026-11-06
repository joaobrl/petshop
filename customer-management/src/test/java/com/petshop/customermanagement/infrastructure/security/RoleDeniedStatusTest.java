package com.petshop.customermanagement.infrastructure.security;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.customermanagement.support.TestJwtService;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

/**
 * SDD-034 RF1: usuário autenticado sem o papel exigido numa rota hasRole
 * deve receber 403 (não 401); sem token continua 401. Usa servidor real
 * (RANDOM_PORT): o MockMvc não faz o dispatch ERROR do container, então não
 * reproduz o problema.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoleDeniedStatusTest {

    private static final TestJwtService TEST_JWT_SERVICE =
            new TestJwtService();

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    @MockitoBean
    private CustomerPortOut customerPortOut;

    @MockitoBean
    private BookingHistoryPortOut bookingHistoryPortOut;

    @MockitoBean
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    @MockitoBean
    private NotificationPortOut notificationPortOut;

    private HttpResponse<String> get(String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/staff/all")).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void customerTokenOnAdminRouteReturns403() throws Exception {
        var token = TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "maria@mail.com", "Maria",
                "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER);

        assertThat(get(token).statusCode()).isEqualTo(403);
    }

    @Test
    void noTokenOnAdminRouteReturns401() throws Exception {
        assertThat(get(null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> getPath(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String adminToken() {
        return TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "admin@petshop.local", "Admin",
                "00000000000", "11000000000", Role.ADMIN, AccountType.STAFF);
    }

    @Test
    @DisplayName("Deve responder 200 em staff/all quando o token de serviço tiver svc:customers:staff-read")
    void shouldReturn200OnStaffAllWhenServiceTokenHasStaffRead() throws Exception {
        var response = getPath("/api/v1/staff/all", TEST_JWT_SERVICE.generateServiceToken("staff-service"));

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Deve responder 403 em staff/all quando o token de serviço tiver outra permissão svc")
    void shouldReturn403OnStaffAllWhenServiceTokenHasOtherPermission() throws Exception {
        var response = getPath("/api/v1/staff/all", TEST_JWT_SERVICE.generateServiceToken("order-service"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Deve responder 200 em staff/all quando o token for de ADMIN")
    void shouldReturn200OnStaffAllWhenTokenIsAdmin() throws Exception {
        var response = getPath("/api/v1/staff/all", adminToken());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("Deve liberar find/customer quando o token de serviço tiver svc:customers:customer-read")
    void shouldAllowFindCustomerWhenServiceTokenHasCustomerRead() throws Exception {
        var response = getPath("/api/v1/customers/find/customer/" + UUID.randomUUID(),
                TEST_JWT_SERVICE.generateServiceToken("order-service"));

        assertThat(response.statusCode()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("Deve responder 403 em rota de usuário quando o token for de serviço")
    void shouldReturn403OnUserRouteWhenTokenIsServiceToken() throws Exception {
        var response = getPath("/api/v1/customers/list/customers", TEST_JWT_SERVICE.generateServiceToken("order-service"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Deve responder 401 quando o aud do token for de outro serviço")
    void shouldReturn401WhenTokenAudienceIsFromAnotherService() throws Exception {
        var userToken = TEST_JWT_SERVICE.generateTokenWithAudience("order-service", UUID.randomUUID(),
                Role.ADMIN, AccountType.STAFF);
        var serviceToken = TEST_JWT_SERVICE.generateServiceTokenWithAudience("staff-service", "order-service");

        assertThat(getPath("/api/v1/customers/list/customers", userToken).statusCode()).isEqualTo(401);
        assertThat(getPath("/api/v1/staff/all", serviceToken).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> patchPath(String path, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method("PATCH", HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String tokenFor(UUID userId, Role role, AccountType type) {
        return TEST_JWT_SERVICE.generateToken(userId, "user@mail.com", "User", "12345678900", "11999999999", role, type);
    }

    @Test
    @DisplayName("Deve liberar reactivate/customer quando o token for de ADMIN")
    void shouldAllowReactivateCustomerWhenTokenIsAdmin() throws Exception {
        var response = patchPath("/api/v1/customers/reactivate/customer/" + UUID.randomUUID(), adminToken());

        assertThat(response.statusCode()).isNotIn(401, 403);
    }

    @Test
    @DisplayName("Deve responder 403 em reactivate/customer quando o token for de RECEPTIONIST")
    void shouldReturn403OnReactivateCustomerWhenTokenIsReceptionist() throws Exception {
        var token = tokenFor(UUID.randomUUID(), Role.RECEPTIONIST, AccountType.STAFF);

        var response = patchPath("/api/v1/customers/reactivate/customer/" + UUID.randomUUID(), token);

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Deve responder 403 em reactivate/customer quando o token for de CUSTOMER com o próprio id")
    void shouldReturn403OnReactivateCustomerWhenCustomerUsesOwnId() throws Exception {
        var id = UUID.randomUUID();

        var response = patchPath("/api/v1/customers/reactivate/customer/" + id, tokenFor(id, Role.CUSTOMER, AccountType.CUSTOMER));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Deve responder 403 em reactivate/customer quando o token for de serviço")
    void shouldReturn403OnReactivateCustomerWhenTokenIsServiceToken() throws Exception {
        var response = patchPath("/api/v1/customers/reactivate/customer/" + UUID.randomUUID(),
                TEST_JWT_SERVICE.generateServiceToken("order-service"));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    @DisplayName("Deve responder 401 em reactivate/customer quando não houver token")
    void shouldReturn401OnReactivateCustomerWhenNoToken() throws Exception {
        var response = patchPath("/api/v1/customers/reactivate/customer/" + UUID.randomUUID(), null);

        assertThat(response.statusCode()).isEqualTo(401);
    }
}
