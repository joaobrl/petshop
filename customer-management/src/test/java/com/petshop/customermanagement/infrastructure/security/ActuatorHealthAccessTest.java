package com.petshop.customermanagement.infrastructure.security;

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
 * k8s (k8s/api-clientes/deployment.yaml) — que não manda token, é só o
 * kubelet checando "tá de pé?" — caía no anyRequest().hasAnyRole(...) e
 * tomava 401 pra sempre, deixando o pod preso fora de Ready. Ver comentário
 * em SecurityConfig.
 *
 * Testcontainers Mongo real aqui (não o default localhost:27017) porque o
 * health check agrega o indicador do Mongo — sem um Mongo de verdade
 * alcançável, a checagem trava até o timeout de 30s do driver e devolve 503,
 * mascarando o que este teste realmente verifica (ausência de auth).
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
