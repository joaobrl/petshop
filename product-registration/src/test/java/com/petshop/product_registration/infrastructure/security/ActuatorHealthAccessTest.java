package com.petshop.product_registration.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão: sem permitAll() pro Actuator, o probe de liveness/readiness do
 * k8s (não manda token, só checa "tá de pé?") caía no
 * anyRequest().authenticated() e tomava 401, deixando o pod fora de Ready.
 * Ver SecurityConfig.
 */
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
