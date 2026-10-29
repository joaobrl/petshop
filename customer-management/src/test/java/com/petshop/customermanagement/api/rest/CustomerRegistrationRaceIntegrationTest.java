package com.petshop.customermanagement.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.customermanagement.core.port.in.dto.CustomerRequestDto;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.repository.CustomerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * SDD-001 CA1 ponta a ponta: dois cadastros do mesmo CPF passam pelo
 * findByCpf (ambos veem "não existe") antes de qualquer save; a constraint
 * unique do Postgres real barra o segundo no commit e o resultado precisa
 * ser 201 + 409 "Resource conflict" (nunca 500).
 *
 * A corrida é determinística: o spy do CustomerPortOut segura cada thread
 * numa CyclicBarrier(2) logo depois do findByCpf real, então as duas só
 * seguem para o save quando ambas já checaram.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CustomerRegistrationRaceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");

    // O application.yaml de teste fixa o dialeto do H2.
    @DynamicPropertySource
    static void postgresDialect(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @MockitoSpyBean
    private CustomerPortOut customerPortOut;

    @MockitoBean
    private BookingHistoryPortOut bookingHistoryPortOut;

    @MockitoBean
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    @MockitoBean
    private NotificationPortOut notificationPortOut;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void concurrentRegistrationsOfSameCpfYieldOneCreatedAndOneConflict() throws Exception {
        var barrier = new CyclicBarrier(2);
        doAnswer(inv -> {
            var result = inv.callRealMethod();
            barrier.await(30, TimeUnit.SECONDS);
            return result;
        }).when(customerPortOut).findByCpf(anyString());

        var cpf = "52998224725";
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<org.springframework.test.web.servlet.MvcResult> a = executor.submit(() -> register(cpf, "a@mail.com"));
            Future<org.springframework.test.web.servlet.MvcResult> b = executor.submit(() -> register(cpf, "b@mail.com"));
            var results = List.of(a.get(60, TimeUnit.SECONDS), b.get(60, TimeUnit.SECONDS));

            var statuses = results.stream().map(r -> r.getResponse().getStatus()).sorted().toList();
            assertThat(statuses).containsExactly(201, 409);

            var conflict = results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElseThrow();
            var body = objectMapper.readTree(conflict.getResponse().getContentAsString());
            assertThat(body.get("title").asText()).isEqualTo("Resource conflict");
            assertThat(customerRepository.findByCpf(cpf)).isPresent();
        } finally {
            executor.shutdownNow();
        }
    }

    private org.springframework.test.web.servlet.MvcResult register(String cpf, String email) throws Exception {
        var request = new CustomerRequestDto("Maria", cpf, email, "11999999999", "Senha123", "Senha123");
        return mockMvc.perform(post("/api/v1/customers/register/customer")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
    }
}
