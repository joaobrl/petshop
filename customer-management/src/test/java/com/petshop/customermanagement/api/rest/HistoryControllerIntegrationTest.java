package com.petshop.customermanagement.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.customermanagement.core.domain.PurchaseHistoryItem;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerHistoryBookings;
import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerPurchaseHistory;
import com.petshop.customermanagement.infrastructure.persistence.mongo.repository.CustomerHistoryBookingsMongoRepository;
import com.petshop.customermanagement.infrastructure.persistence.mongo.repository.CustomerPurchaseHistoryMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class HistoryControllerIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    private static final JwtService TEST_JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerHistoryBookingsMongoRepository bookingRepository;

    @Autowired
    private CustomerPurchaseHistoryMongoRepository purchaseRepository;

    @MockitoBean
    private CustomerPortOut customerPortOut;

    private UUID ownCustomerId;
    private UUID otherCustomerId;

    @BeforeEach
    void seedMongo() {
        bookingRepository.deleteAll();
        purchaseRepository.deleteAll();

        ownCustomerId = UUID.randomUUID();
        otherCustomerId = UUID.randomUUID();

        bookingRepository.save(bookingFor(ownCustomerId, "Banho"));
        bookingRepository.save(bookingFor(otherCustomerId, "Tosa"));

        purchaseRepository.save(purchaseFor(ownCustomerId));
        purchaseRepository.save(purchaseFor(otherCustomerId));
    }

    private CustomerHistoryBookings bookingFor(UUID customerId, String serviceType) {
        var entity = new CustomerHistoryBookings();
        entity.setBookingId(UUID.randomUUID());
        entity.setCustomerId(customerId);
        entity.setServiceType(serviceType);
        entity.setBookingDate("10/08/2026");
        entity.setBookingTime("14:00");
        entity.setStatus("CONFIRMED");
        return entity;
    }

    private CustomerPurchaseHistory purchaseFor(UUID customerId) {
        var entity = new CustomerPurchaseHistory();
        entity.setOrderId(UUID.randomUUID());
        entity.setCustomerId(customerId);
        entity.setItems(List.of(new PurchaseHistoryItem(1L, "Ração", 2, 50.0)));
        entity.setTotalAmount(100.0);
        entity.setPaidAt(LocalDateTime.now());
        return entity;
    }

    private static RequestPostProcessor customerAuth(UUID customerId) {
        return bearer(TEST_JWT_SERVICE.generateToken(customerId, "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER));
    }

    private static RequestPostProcessor receptionistAuth() {
        return bearer(TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "recepcao@petshop.local", "Recepcao", "00000000001", "11000000001", Role.RECEPTIONIST, AccountType.STAFF));
    }

    private static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    @Nested
    class HistoricoDeServicos {

        @Test
        void clienteConsultaHistoricoDeServicosDelaMesma_sucesso() throws Exception {
            mockMvc.perform(get("/api/v1/history/bookings/" + ownCustomerId).with(customerAuth(ownCustomerId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", is(1)))
                    .andExpect(jsonPath("$[0].customerId", is(ownCustomerId.toString())))
                    .andExpect(jsonPath("$[0].serviceType", is("Banho")));
        }

        @Test
        void clienteConsultaHistoricoDeServicosDeOutroCliente_falha() throws Exception {
            mockMvc.perform(get("/api/v1/history/bookings/" + otherCustomerId).with(customerAuth(ownCustomerId)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void clienteConsultaHistoricoDeServicoGeral_falha() throws Exception {
            mockMvc.perform(get("/api/v1/history/bookings").with(customerAuth(ownCustomerId)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void recepcionistaConsultaHistoricoDeServicosDeOutroCliente_sucesso() throws Exception {
            mockMvc.perform(get("/api/v1/history/bookings/" + otherCustomerId).with(receptionistAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", is(1)))
                    .andExpect(jsonPath("$[0].customerId", is(otherCustomerId.toString())))
                    .andExpect(jsonPath("$[0].serviceType", is("Tosa")));
        }

        @Test
        void recepcionistaConsultaHistoricoDeServicoGeral_sucesso() throws Exception {
            mockMvc.perform(get("/api/v1/history/bookings").with(receptionistAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()", is(2)));
        }
    }

    @Nested
    class HistoricoDeCompras {

        @Test
        void clienteConsultaHistoricoDeComprasDelaMesma_sucesso() throws Exception {
            mockMvc.perform(get("/api/v1/history/purchases/" + ownCustomerId).with(customerAuth(ownCustomerId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", is(1)))
                    .andExpect(jsonPath("$[0].customerId", is(ownCustomerId.toString())));
        }

        @Test
        void clienteConsultaHistoricoDeComprasDeOutroCliente_falha() throws Exception {
            mockMvc.perform(get("/api/v1/history/purchases/" + otherCustomerId).with(customerAuth(ownCustomerId)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void clienteConsultaHistoricoDeComprasGeral_falha() throws Exception {
            mockMvc.perform(get("/api/v1/history/purchases").with(customerAuth(ownCustomerId)))
                    .andExpect(status().isForbidden());
        }

        @Test
        void recepcionistaConsultaHistoricoDeComprasDeOutroCliente_sucesso() throws Exception {
            mockMvc.perform(get("/api/v1/history/purchases/" + otherCustomerId).with(receptionistAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", is(1)))
                    .andExpect(jsonPath("$[0].customerId", is(otherCustomerId.toString())));
        }

        @Test
        void recepcionistaConsultaHistoricoDeComprasGeral_sucesso() throws Exception {
            mockMvc.perform(get("/api/v1/history/purchases").with(receptionistAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()", is(2)));
        }
    }
}
