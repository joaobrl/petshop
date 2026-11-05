package com.petshop.staff_service.infrastructure.security;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.staff_service.core.domain.AvailabilityResult;
import com.petshop.staff_service.core.port.in.AvailabilityPortIn;
import com.petshop.staff_service.core.port.in.WeekendAllocationPortIn;
import com.petshop.staff_service.core.port.out.StaffPortOut;
import com.petshop.staff_service.core.port.out.WeekendAllocationPortOut;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Segredo tem que bater com src/test/resources/application.yml
 * (jwt.secret: test-secret-key-for-jwt-signing-min-32-bytes-long).
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaffSecurityIntegrationTest {

    private static final JwtService TEST_JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AvailabilityPortIn availabilityPortIn;
    @MockitoBean
    private WeekendAllocationPortIn weekendAllocationPortIn;
    @MockitoBean
    private WeekendAllocationPortOut weekendAllocationPortOut;
    @MockitoBean
    private StaffPortOut staffPortOut;

    private String token(Role role) {
        return TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "user@petshop.com", "User", "12345678900",
                "11999990000", role, AccountType.STAFF);
    }

    private String serviceToken() {
        return TEST_JWT_SERVICE.generateServiceToken("booking-service");
    }

    private RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    // Sem token — é assim que o kubelet chama o liveness/readiness probe (ver SecurityConfig).
    @Test
    void actuatorHealthIsPublicAndDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void availabilityGeneralRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/availability/general")
                        .param("dateTime", "2026-08-10T09:00:00")
                        .param("role", "GROOMER"))
                .andExpect(status().isUnauthorized());
    }

    // /availability/** é chamado internamente pelo booking-service, que repassa o token de
    // quem originou a requisição quando há um (relay-com-fallback) — por isso aceita tanto um
    // token de serviço quanto o de um usuário autenticado (cliente ou funcionário).
    @Test
    void availabilityGeneralAllowedForCustomerToken() throws Exception {
        org.mockito.Mockito.when(availabilityPortIn.checkGeneralAvailability(
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.any(Role.class)))
                .thenReturn(new AvailabilityResult(1, true, LocalDateTime.of(2026, 8, 10, 9, 0)));

        mockMvc.perform(get("/api/v1/availability/general")
                        .param("dateTime", "2026-08-10T09:00:00")
                        .param("role", "GROOMER")
                        .with(bearer(token(Role.CUSTOMER))))
                .andExpect(status().isOk());
    }

    @Test
    void availabilityGeneralAllowedForStaffToken() throws Exception {
        org.mockito.Mockito.when(availabilityPortIn.checkGeneralAvailability(
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.any(Role.class)))
                .thenReturn(new AvailabilityResult(1, true, LocalDateTime.of(2026, 8, 10, 9, 0)));

        mockMvc.perform(get("/api/v1/availability/general")
                        .param("dateTime", "2026-08-10T09:00:00")
                        .param("role", "GROOMER")
                        .with(bearer(token(Role.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void availabilityGeneralAllowedForServiceToken() throws Exception {
        org.mockito.Mockito.when(availabilityPortIn.checkGeneralAvailability(
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.any(Role.class)))
                .thenReturn(new AvailabilityResult(1, true, LocalDateTime.of(2026, 8, 10, 9, 0)));

        mockMvc.perform(get("/api/v1/availability/general")
                        .param("dateTime", "2026-08-10T09:00:00")
                        .param("role", "GROOMER")
                        .with(bearer(serviceToken())))
                .andExpect(status().isOk());
    }

    @Test
    void weekendAllocationGenerateRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/weekend-allocation/generate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void weekendAllocationGenerateForbiddenForNonAdminRole() throws Exception {
        mockMvc.perform(post("/api/v1/weekend-allocation/generate")
                        .with(bearer(token(Role.GROOMER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void weekendAllocationGenerateAllowedForAdminRole() throws Exception {
        org.mockito.Mockito.when(weekendAllocationPortIn.generateNextMonth()).thenReturn(List.of());

        mockMvc.perform(post("/api/v1/weekend-allocation/generate")
                        .with(bearer(token(Role.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void weekendAllocationListForbiddenForNonAdminRole() throws Exception {
        mockMvc.perform(get("/api/v1/weekend-allocation")
                        .param("date", "2026-08-01")
                        .with(bearer(token(Role.RECEPTIONIST))))
                .andExpect(status().isForbidden());
    }

    @Test
    void weekendAllocationListAllowedForAdminRole() throws Exception {
        org.mockito.Mockito.when(weekendAllocationPortOut.findByDate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/weekend-allocation")
                        .param("date", "2026-08-01")
                        .with(bearer(token(Role.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void availabilityGeneralAcceptsIsoDateTime() throws Exception {
        org.mockito.Mockito.when(availabilityPortIn.checkGeneralAvailability(
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.any(Role.class)))
                .thenReturn(new AvailabilityResult(1, true, LocalDateTime.of(2026, 8, 15, 9, 0)));

        mockMvc.perform(get("/api/v1/availability/general")
                        .param("dateTime", "2026-08-15T09:00:00")
                        .param("role", "GROOMER")
                        .with(bearer(token(Role.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.validatedDateTime").value("2026-08-15T09:00:00"));
    }

    @Test
    void availabilityGeneralRejectsBrazilianDateTimeWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/availability/general")
                        .param("dateTime", "15/08/2026 09:00")
                        .param("role", "GROOMER")
                        .with(bearer(token(Role.ADMIN))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void availabilityScheduleAcceptsIsoDateTime() throws Exception {
        org.mockito.Mockito.when(availabilityPortIn.scheduledStaffFor(
                        org.mockito.ArgumentMatchers.any(LocalDateTime.class), org.mockito.ArgumentMatchers.any(Role.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/availability/schedule")
                        .param("dateTime", "2026-08-15T09:00:00")
                        .param("role", "GROOMER")
                        .with(bearer(token(Role.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void availabilityScheduleRejectsBrazilianDateTimeWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/availability/schedule")
                        .param("dateTime", "15/08/2026 09:00")
                        .param("role", "GROOMER")
                        .with(bearer(token(Role.ADMIN))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weekendAllocationAcceptsIsoDate() throws Exception {
        org.mockito.Mockito.when(weekendAllocationPortOut.findByDate(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/weekend-allocation")
                        .param("date", "2026-11-15")
                        .with(bearer(token(Role.ADMIN))))
                .andExpect(status().isOk());
    }

    @Test
    void weekendAllocationRejectsBrazilianDateWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/weekend-allocation")
                        .param("date", "15/11/2026")
                        .with(bearer(token(Role.ADMIN))))
                .andExpect(status().isBadRequest());
    }
}
