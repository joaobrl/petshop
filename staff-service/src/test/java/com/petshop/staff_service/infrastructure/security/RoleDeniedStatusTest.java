package com.petshop.staff_service.infrastructure.security;

import com.petshop.staff_service.core.port.in.AvailabilityPortIn;
import com.petshop.staff_service.core.port.in.WeekendAllocationPortIn;
import com.petshop.staff_service.core.port.out.StaffPortOut;
import com.petshop.staff_service.core.port.out.WeekendAllocationPortOut;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.staff_service.support.TestJwtService;
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
class RoleDeniedStatusTest {

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
    private AvailabilityPortIn availabilityPortIn;
    @MockitoBean
    private WeekendAllocationPortIn weekendAllocationPortIn;
    @MockitoBean
    private WeekendAllocationPortOut weekendAllocationPortOut;
    @MockitoBean
    private StaffPortOut staffPortOut;

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER", "RECEPTIONIST", "GROOMER", "VETERINARIAN"})
    @DisplayName("Deve responder 403 problem+json para papel sem permissão na rota restrita")
    void shouldReturn403ProblemJsonWhenRoleLacksPermission(Role role) throws Exception {
        var response = send("GET", "/api/v1/weekend-allocation", token(role));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/problem+json");
        assertThat(response.body())
                .contains("\"title\":\"Acesso negado\"")
                .contains("\"status\":403")
                .contains("\"instance\":\"/api/v1/weekend-allocation\"");
    }

    @Test
    @DisplayName("Deve responder 401 quando a rota restrita for chamada sem token")
    void shouldReturn401WhenNoTokenOnRestrictedRoute() throws Exception {
        var response = send("GET", "/api/v1/weekend-allocation", null);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    @DisplayName("Deve responder 401 quando o token for inválido")
    void shouldReturn401WhenTokenIsInvalid() throws Exception {
        var response = send("GET", "/api/v1/weekend-allocation", "token-invalido");

        assertThat(response.statusCode()).isEqualTo(401);
    }
}
