package com.petshop.booking_service.infrastructure.security;

import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.port.in.AvailableSlotsPortIn;
import com.petshop.booking_service.core.port.in.BookingPortIn;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Segredo tem que bater com src/test/resources/application.yml
 * (jwt.secret: test-secret-key-for-jwt-signing-min-32-bytes-long).
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingSecurityIntegrationTest {

    private static final JwtService TEST_JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingPortIn bookingPortIn;
    @MockitoBean
    private AvailableSlotsPortIn availableSlotsPortIn;

    private String token(Role role) {
        return TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "user@petshop.com", "User", "12345678900",
                "11999990000", role, AccountType.CUSTOMER);
    }

    private RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    @Test
    void availableSlotsIsPublicAndDoesNotRequireAuthentication() throws Exception {
        org.mockito.Mockito.when(availableSlotsPortIn.findAvailableSlots(
                        org.mockito.ArgumentMatchers.any(ServiceType.class),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(RangeType.class)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bookings/available-slots")
                        .param("serviceType", "BANHO")
                        .param("date", "10/08/2026")
                        .param("range", "DAY"))
                .andExpect(status().isOk());
    }

    // Sem token — é assim que o kubelet chama o liveness/readiness probe
    // (k8s/api-booking/deployment.yaml). Ver comentário em SecurityConfig.
    @Test
    void actuatorHealthIsPublicAndDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void listBookingsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/list"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listBookingsAllowsAnyAuthenticatedRole() throws Exception {
        org.mockito.Mockito.when(bookingPortIn.findBookings(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/bookings/list")
                        .with(bearer(token(Role.CUSTOMER))))
                .andExpect(status().isOk());
    }

    // ---------- confirmação de pagamento ----------

    @Test
    void confirmPaymentRequiresAuthentication() throws Exception {
        mockMvc.perform(patch("/api/v1/bookings/confirm-payment/{id}", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"PIX\"}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"CUSTOMER", "GROOMER", "VETERINARIAN"})
    void confirmPaymentForbidsNonReceptionOrAdminRoles(Role role) throws Exception {
        mockMvc.perform(patch("/api/v1/bookings/confirm-payment/{id}", UUID.randomUUID())
                        .with(bearer(token(role)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"PIX\"}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"ADMIN", "RECEPTIONIST"})
    void confirmPaymentAllowsAdminAndReceptionistRoles(Role role) throws Exception {
        var id = UUID.randomUUID();
        var booking = new com.petshop.booking_service.core.domain.Booking();
        booking.setId(id);
        booking.setServiceDetails(new com.petshop.booking_service.core.domain.ServiceDetails(ServiceType.BANHO));
        org.mockito.Mockito.when(bookingPortIn.confirmPayment(org.mockito.ArgumentMatchers.eq(id), org.mockito.ArgumentMatchers.any()))
                .thenReturn(booking);

        mockMvc.perform(patch("/api/v1/bookings/confirm-payment/{id}", id)
                        .with(bearer(token(role)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentMethod\":\"PIX\"}"))
                .andExpect(status().isOk());
    }
}
