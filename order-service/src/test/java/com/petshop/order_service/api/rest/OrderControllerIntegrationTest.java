package com.petshop.order_service.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.order_service.core.domain.CustomerInfo;
import com.petshop.order_service.core.domain.ProductStockInfo;
import com.petshop.order_service.core.port.in.dto.AddCartItemRequestDto;
import com.petshop.order_service.core.port.out.CustomerPortOut;
import com.petshop.order_service.core.port.out.NotificationPortOut;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import com.petshop.order_service.core.port.out.PurchaseHistoryPortOut;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes ponta a ponta: MockMvc + SecurityFilterChain reais + OrderService/
 * CartService reais com H2 em memória; só Feign e Kafka são mockados. Todo
 * /api/v1/orders/** exige autenticação (só /api/v1/cart/** é público).
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerIntegrationTest {

    private static final JwtService TEST_JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ProductStockPortOut productStockPortOut;
    @MockitoBean
    private CustomerPortOut customerPortOut;
    @MockitoBean
    private NotificationPortOut notificationPortOut;
    @MockitoBean
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    private static RequestPostProcessor customerAuth(UUID customerId) {
        return bearer(TEST_JWT_SERVICE.generateToken(customerId, "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER));
    }

    private static RequestPostProcessor adminAuth() {
        return bearer(TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "admin@petshop.local", "Admin", "00000000000", "11000000000", Role.ADMIN, AccountType.STAFF));
    }

    private static RequestPostProcessor bearer(String token) {
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private void addItem(UUID customerId, RequestPostProcessor auth) throws Exception {
        var dto = new AddCartItemRequestDto();
        dto.setCustomerId(customerId);
        dto.setProductId(1L);
        dto.setQuantity(2);
        when(productStockPortOut.reserve(1L, 2)).thenReturn(new ProductStockInfo(1L, "Racao", 79.90));

        mockMvc.perform(post("/api/v1/cart/items")
                .with(auth)
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(dto)));
    }

    private UUID checkoutAndGetOrderId(UUID customerId) throws Exception {
        addItem(customerId, customerAuth(customerId));
        when(customerPortOut.findCustomerById(customerId))
                .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));

        var response = mockMvc.perform(post("/api/v1/orders/checkout")
                        .param("customerId", customerId.toString())
                        .with(customerAuth(customerId)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(response.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    @Nested
    class Checkout {

        @Test
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/v1/orders/checkout").param("customerId", UUID.randomUUID().toString()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void returnsBusinessRuleViolationWhenThereIsNoOpenCart() throws Exception {
            var customerId = UUID.randomUUID();

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title", is("Business rule violation")));
        }

        @Test
        void returnsBusinessRuleViolationWhenCartIsEmpty() throws Exception {
            var customerId = UUID.randomUUID();
            addItem(customerId, customerAuth(customerId));
            mockMvc.perform(delete("/api/v1/cart/items/1")
                    .param("customerId", customerId.toString())
                    .with(customerAuth(customerId)));

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail", is("Não é possível finalizar um carrinho vazio.")));
        }

        @Test
        void succeedsAndReturnsCreatedOrder() throws Exception {
            var customerId = UUID.randomUUID();
            addItem(customerId, customerAuth(customerId));
            when(customerPortOut.findCustomerById(customerId))
                    .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.customerId", is(customerId.toString())))
                    .andExpect(jsonPath("$.status", is("AWAITING_PAYMENT")))
                    .andExpect(header().exists("Location"));
        }

        @Test
        void returnsNotFoundWhenCustomerDoesNotExist() throws Exception {
            var customerId = UUID.randomUUID();
            addItem(customerId, customerAuth(customerId));
            when(customerPortOut.findCustomerById(customerId)).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetOrderById {

        @Test
        void requiresAuthentication() throws Exception {
            mockMvc.perform(get("/api/v1/orders/" + UUID.randomUUID()))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void returnsNotFoundWhenOrderDoesNotExist() throws Exception {
            mockMvc.perform(get("/api/v1/orders/" + UUID.randomUUID()).with(adminAuth()))
                    .andExpect(status().isNotFound());
        }

        @Test
        void ownerCanSeeTheirOwnOrder() throws Exception {
            var customerId = UUID.randomUUID();
            var orderId = checkoutAndGetOrderId(customerId);

            mockMvc.perform(get("/api/v1/orders/" + orderId).with(customerAuth(customerId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(orderId.toString())));
        }

        @Test
        void anotherCustomerCannotSeeSomeoneElsesOrder() throws Exception {
            var customerId = UUID.randomUUID();
            var orderId = checkoutAndGetOrderId(customerId);

            mockMvc.perform(get("/api/v1/orders/" + orderId).with(customerAuth(UUID.randomUUID())))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title", is("Access denied")));
        }

        @Test
        void staffCanSeeAnyOrder() throws Exception {
            var customerId = UUID.randomUUID();
            var orderId = checkoutAndGetOrderId(customerId);

            mockMvc.perform(get("/api/v1/orders/" + orderId).with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(orderId.toString())));
        }
    }
}
