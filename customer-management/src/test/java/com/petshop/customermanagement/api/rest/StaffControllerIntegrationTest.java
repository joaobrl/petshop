package com.petshop.customermanagement.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.in.dto.StaffRequestDto;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes ponta a ponta: MockMvc real com o SecurityFilterChain/JwtAuthenticationFilter
 * aplicado + StaffService real; só o StaffPortOut é mockado. Cobre as decisões de
 * autorização (todo {@code /api/v1/staff/**} exige ADMIN, exceto {@code GET /staff/all}
 * que também aceita token de SERVICE) que os testes unitários, sem subir o filtro de
 * segurança, não exercitam.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StaffControllerIntegrationTest {

    // Mesmo segredo do src/test/resources/application.yaml.
    private static final JwtService TEST_JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private StaffPortOut staffPortOut;

    private static RequestPostProcessor adminAuth() {
        return bearer(TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "admin@petshop.local", "Admin", "00000000000", "11000000000", Role.ADMIN, AccountType.STAFF));
    }

    private static RequestPostProcessor receptionistAuth() {
        return bearer(TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "recepcao@petshop.local", "Recepcao", "11122233344", "11000000001", Role.RECEPTIONIST, AccountType.STAFF));
    }

    private static RequestPostProcessor customerAuth() {
        return bearer(TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER));
    }

    private static RequestPostProcessor serviceAuth() {
        return bearer(TEST_JWT_SERVICE.generateServiceToken("staff-service"));
    }

    private static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private Staff sampleStaff(UUID id) {
        return new Staff(id, "João", "12345678900", "joao@petshop.com", "11999999999", true, Role.RECEPTIONIST);
    }

    private StaffRequestDto validRequest() {
        var dto = new StaffRequestDto();
        dto.setName("João");
        dto.setCpf("12345678900");
        dto.setEmail("joao@petshop.com");
        dto.setPhone("11999999999");
        dto.setRole(Role.RECEPTIONIST);
        return dto;
    }

    @Nested
    class CreateStaff {

        @Test
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/v1/staff/create")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void forbiddenForCustomerRole() throws Exception {
            mockMvc.perform(post("/api/v1/staff/create")
                            .with(customerAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void forbiddenForReceptionistRole() throws Exception {
            mockMvc.perform(post("/api/v1/staff/create")
                            .with(receptionistAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void succeedsForAdmin() throws Exception {
            when(staffPortOut.findByCpf("12345678900")).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail("joao@petshop.com")).thenReturn(Optional.empty());
            when(staffPortOut.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(post("/api/v1/staff/create")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name", is("João")))
                    .andExpect(jsonPath("$.cpf", is("12345678900")));
        }

        @Test
        void returnsBadRequestForInvalidBody() throws Exception {
            var invalid = new StaffRequestDto();
            invalid.setName("");
            invalid.setCpf("123");

            mockMvc.perform(post("/api/v1/staff/create")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray());
        }

        @Test
        void returnsConflictForDuplicateCpf() throws Exception {
            when(staffPortOut.findByCpf("12345678900")).thenReturn(Optional.of(sampleStaff(UUID.randomUUID())));

            mockMvc.perform(post("/api/v1/staff/create")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title", is("Resource conflict")));
        }

        @Test
        void returnsConflictForDuplicateEmail() throws Exception {
            when(staffPortOut.findByCpf("12345678900")).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail("joao@petshop.com")).thenReturn(Optional.of(sampleStaff(UUID.randomUUID())));

            mockMvc.perform(post("/api/v1/staff/create")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title", is("Resource conflict")));
        }
    }

    @Nested
    class GetAllStaff {

        @Test
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/v1/staff/all"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void forbiddenForNonAdminRole() throws Exception {
            mockMvc.perform(get("/api/v1/staff/all").with(receptionistAuth()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void succeedsForAdmin() throws Exception {
            when(staffPortOut.findAllByEnabledTrue(any())).thenReturn(new PageImpl<>(List.of(sampleStaff(UUID.randomUUID()), sampleStaff(UUID.randomUUID()))));

            mockMvc.perform(get("/api/v1/staff/all").with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()", is(2)));
        }

        @Test
        void returnsEmptyListWhenNoneRegistered() throws Exception {
            when(staffPortOut.findAllByEnabledTrue(any())).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/api/v1/staff/all").with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()", is(0)));
        }

        @Test
        void succeedsForServiceToken() throws Exception {
            when(staffPortOut.findAllByEnabledTrue(any())).thenReturn(new PageImpl<>(List.of(sampleStaff(UUID.randomUUID()))));

            mockMvc.perform(get("/api/v1/staff/all").with(serviceAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()", is(1)));
        }

        @Test
        void forbiddenForCustomerRole() throws Exception {
            mockMvc.perform(get("/api/v1/staff/all").with(customerAuth()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    class GetStaffById {

        @Test
        void forbiddenForNonAdminRole() throws Exception {
            mockMvc.perform(get("/api/v1/staff/" + UUID.randomUUID() + "/find").with(customerAuth()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void succeedsForAdmin() throws Exception {
            var id = UUID.randomUUID();
            when(staffPortOut.findById(id)).thenReturn(Optional.of(sampleStaff(id)));

            mockMvc.perform(get("/api/v1/staff/" + id + "/find").with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())));
        }

        @Test
        void succeedsForAdminWhenSearchingByCpf() throws Exception {
            var staff = sampleStaff(UUID.randomUUID());
            when(staffPortOut.findByCpf("12345678900")).thenReturn(Optional.of(staff));

            mockMvc.perform(get("/api/v1/staff/12345678900/find").with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(staff.getId().toString())));
        }

        @Test
        void returnsNotFoundWhenMissing() throws Exception {
            var id = UUID.randomUUID();
            when(staffPortOut.findById(id)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/staff/" + id + "/find").with(adminAuth()))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource not found")));
        }
    }

    @Nested
    class UpdateStaff {

        @Test
        void forbiddenForNonAdminRole() throws Exception {
            mockMvc.perform(patch("/api/v1/staff/" + UUID.randomUUID() + "/update")
                            .with(receptionistAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void succeedsForAdmin() throws Exception {
            var id = UUID.randomUUID();
            when(staffPortOut.findById(id)).thenReturn(Optional.of(sampleStaff(id)));
            when(staffPortOut.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

            var update = new StaffRequestDto();
            update.setName("João Atualizado");

            mockMvc.perform(patch("/api/v1/staff/" + id + "/update")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("João Atualizado")));
        }

        @Test
        void returnsNotFoundWhenMissing() throws Exception {
            var id = UUID.randomUUID();
            when(staffPortOut.findById(id)).thenReturn(Optional.empty());

            mockMvc.perform(patch("/api/v1/staff/" + id + "/update")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class DeleteStaff {

        @Test
        void forbiddenForNonAdminRole() throws Exception {
            mockMvc.perform(delete("/api/v1/staff/" + UUID.randomUUID() + "/delete").with(customerAuth()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void succeedsForAdmin() throws Exception {
            var id = UUID.randomUUID();
            when(staffPortOut.findById(id)).thenReturn(Optional.of(sampleStaff(id)));
            when(staffPortOut.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(delete("/api/v1/staff/" + id + "/delete").with(adminAuth()))
                    .andExpect(status().isNoContent());
        }

        @Test
        void returnsNotFoundWhenMissing() throws Exception {
            var id = UUID.randomUUID();
            when(staffPortOut.findById(id)).thenReturn(Optional.empty());

            mockMvc.perform(delete("/api/v1/staff/" + id + "/delete").with(adminAuth()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class ReactivateStaff {

        @Test
        void forbiddenForNonAdminRole() throws Exception {
            mockMvc.perform(patch("/api/v1/staff/" + UUID.randomUUID() + "/reactivate").with(customerAuth()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void succeedsForAdmin() throws Exception {
            var id = UUID.randomUUID();
            var staff = sampleStaff(id);
            staff.setEnabled(false);
            when(staffPortOut.findById(id)).thenReturn(Optional.of(staff));
            when(staffPortOut.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(patch("/api/v1/staff/" + id + "/reactivate").with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(id.toString())));
        }

        @Test
        void returnsNotFoundWhenMissing() throws Exception {
            var id = UUID.randomUUID();
            when(staffPortOut.findById(id)).thenReturn(Optional.empty());

            mockMvc.perform(patch("/api/v1/staff/" + id + "/reactivate").with(adminAuth()))
                    .andExpect(status().isNotFound());
        }
    }
}
