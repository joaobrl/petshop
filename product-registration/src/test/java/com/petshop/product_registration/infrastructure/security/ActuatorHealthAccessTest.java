package com.petshop.product_registration.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regressão: sem permitAll() pro Actuator, o probe de liveness/readiness do
 * k8s (não manda token, só checa "tá de pé?") caía no
 * anyRequest().authenticated() e tomava 401, deixando o pod fora de Ready.
 * Ver SecurityConfig.
 *
 * Testcontainers Mongo real aqui (não o default localhost:27017): com
 * `auto-index-creation: true`, o contexto tenta criar o índice único de
 * `ProductDocument.name` já na subida, o que exige um Mongo de verdade
 * alcançável — sem isso o contexto nem sobe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ActuatorHealthAccessTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealthIsPublicAndDoesNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
