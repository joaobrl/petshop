package com.petshop.order_service.api.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.order_service.core.domain.CustomerInfo;
import com.petshop.order_service.core.domain.ProductStockInfo;
import com.petshop.order_service.core.port.in.dto.AddCartItemRequestDto;
import com.petshop.order_service.core.port.in.dto.CheckoutRequestDto;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    private static ProductStockInfo onSale(Long id) {
        return new ProductStockInfo(id, "Produto " + id, 79.90, true, "50+");
    }

    private void productsOnSale() {
        when(productStockPortOut.getInfo(any(), any())).thenAnswer(inv -> onSale(inv.getArgument(0)));
        when(productStockPortOut.getInfos(any(), any())).thenReturn(Map.of(1L, onSale(1L), 2L, onSale(2L)));
    }

    private void addItem(UUID customerId, Long productId) throws Exception {
        var dto = new AddCartItemRequestDto();
        dto.setCustomerId(customerId);
        dto.setProductId(productId);
        dto.setQuantity(2);

        mockMvc.perform(post("/api/v1/cart/items")
                        .with(customerAuth(customerId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());
    }

    private void customerExists(UUID customerId) {
        when(customerPortOut.findCustomerById(eq(customerId), any()))
                .thenReturn(Optional.of(new CustomerInfo(customerId, "Maria", "maria@mail.com")));
    }

    private String selection(Long... productIds) throws Exception {
        return objectMapper.writeValueAsString(new CheckoutRequestDto(List.of(productIds)));
    }

    private UUID checkoutAndGetOrderId(UUID customerId) throws Exception {
        productsOnSale();
        addItem(customerId, 1L);
        customerExists(customerId);

        var response = mockMvc.perform(post("/api/v1/orders/checkout")
                        .param("customerId", customerId.toString())
                        .with(customerAuth(customerId))
                        .contentType("application/json")
                        .content(selection(1L)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(response.getResponse().getContentAsString());
        return UUID.fromString(body.get("id").asText());
    }

    @Nested
    class Checkout {

        @Test
        void requiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", UUID.randomUUID().toString())
                            .contentType("application/json")
                            .content(selection(1L)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void returnsBadRequestWhenBodyIsMissing() throws Exception {
            var customerId = UUID.randomUUID();

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void returnsBusinessRuleViolationWhenNothingSelected() throws Exception {
            var customerId = UUID.randomUUID();

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId))
                            .contentType("application/json")
                            .content(selection()))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail", containsString("Selecione")));
        }

        @Test
        void returnsBusinessRuleViolationWhenThereIsNoOpenCart() throws Exception {
            var customerId = UUID.randomUUID();

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId))
                            .contentType("application/json")
                            .content(selection(1L)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title", is("Business rule violation")));
        }

        @Test
        void returnsBusinessRuleViolationWhenSelectedItemIsNotInCart() throws Exception {
            var customerId = UUID.randomUUID();
            productsOnSale();
            addItem(customerId, 1L);

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId))
                            .contentType("application/json")
                            .content(selection(2L)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail", containsString("não estão no carrinho")));
        }

        @Test
        void checksOutOnlySelectedItemsAndKeepsTheRestInTheCart() throws Exception {
            var customerId = UUID.randomUUID();
            productsOnSale();
            addItem(customerId, 1L);
            addItem(customerId, 2L);
            customerExists(customerId);

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId))
                            .contentType("application/json")
                            .content(selection(1L)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status", is("AWAITING_PAYMENT")))
                    .andExpect(jsonPath("$.items.length()", is(1)))
                    .andExpect(jsonPath("$.items[0].productId", is(1)))
                    .andExpect(jsonPath("$.expiresAt").exists())
                    .andExpect(header().exists("Location"));

            verify(productStockPortOut).reserve(eq(1L), eq(2), any());
            verify(productStockPortOut, never()).reserve(eq(2L), anyInt(), any());

            mockMvc.perform(get("/api/v1/cart").param("customerId", customerId.toString()).with(customerAuth(customerId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("OPEN")))
                    .andExpect(jsonPath("$.items.length()", is(1)))
                    .andExpect(jsonPath("$.items[0].productId", is(2)));
        }

        @Test
        void returnsUnprocessableEntityListingItemsWithoutStock() throws Exception {
            var customerId = UUID.randomUUID();
            productsOnSale();
            addItem(customerId, 1L);
            customerExists(customerId);
            when(productStockPortOut.reserve(eq(1L), eq(2), any()))
                    .thenThrow(new BusinessRuleException("Estoque insuficiente para o produto 'Produto 1'"));

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId))
                            .contentType("application/json")
                            .content(selection(1L)))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail", is("Estoque insuficiente para o produto 'Produto 1'")));
        }

        @Test
        void repeatingTheIdempotencyKeyReturnsTheSameOrderWith200() throws Exception {
            var customerId = UUID.randomUUID();
            productsOnSale();
            addItem(customerId, 1L);
            customerExists(customerId);
            var key = UUID.randomUUID().toString();

            var first = mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId))
                            .header("Idempotency-Key", key)
                            .contentType("application/json")
                            .content(selection(1L)))
                    .andExpect(status().isCreated())
                    .andReturn();
            var orderId = objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId))
                            .header("Idempotency-Key", key)
                            .contentType("application/json")
                            .content(selection(1L)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(orderId)))
                    .andExpect(header().doesNotExist("Location"));

            verify(productStockPortOut, times(1)).reserve(eq(1L), eq(2), any());
        }

        @Test
        void returnsNotFoundWhenCustomerDoesNotExist() throws Exception {
            var customerId = UUID.randomUUID();
            productsOnSale();
            addItem(customerId, 1L);
            when(customerPortOut.findCustomerById(eq(customerId), any())).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/orders/checkout")
                            .param("customerId", customerId.toString())
                            .with(customerAuth(customerId))
                            .contentType("application/json")
                            .content(selection(1L)))
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
