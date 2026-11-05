package com.petshop.customermanagement.infrastructure.security;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.customermanagement.support.TestJwtService;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
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
}
