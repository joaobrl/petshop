package com.petshop.order_service.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Regressão: sem permitAll() pro Actuator, o probe de liveness/readiness
// do k8s (sem token) toma 401 e o pod nunca fica Ready — ver SecurityConfig.
@SpringBootTest
@AutoConfigureMockMvc
class ActuatorHealthAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealthIsPublicAndDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
