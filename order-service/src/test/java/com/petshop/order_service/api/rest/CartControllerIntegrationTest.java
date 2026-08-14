package com.petshop.order_service.api.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
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
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes ponta a ponta: MockMvc + SecurityFilterChain reais + CartService
 * real com H2 em memória; só Feign e Kafka são mockados. /api/v1/cart/**
 * é público, então o foco é validação e o comportamento sem-login vs.
 * logado (reserva de estoque, resolução do customerId a partir do token).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CartControllerIntegrationTest {

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

    private AddCartItemRequestDto addItemRequest(UUID customerId, Long productId, Integer quantity) {
        var dto = new AddCartItemRequestDto();
        dto.setCustomerId(customerId);
        dto.setProductId(productId);
        dto.setQuantity(quantity);
        return dto;
    }

    @Nested
    class AddItem {

        @Test
        void addsItemWithoutReservingStockWhenAnonymous() throws Exception {
            var customerId = UUID.randomUUID();
            when(productStockPortOut.getInfo(1L)).thenReturn(new ProductStockInfo(1L, "Racao", 79.90));

            mockMvc.perform(post("/api/v1/cart/items")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(addItemRequest(customerId, 1L, 2))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId", is(customerId.toString())))
                    .andExpect(jsonPath("$.items[0].reserved", is(false)));
        }

        @Test
        void addsItemAndReservesStockUsingTokenOwnerAsCustomer() throws Exception {
            var tokenCustomerId = UUID.randomUUID();
            var bodyCustomerId = UUID.randomUUID();
            when(productStockPortOut.reserve(1L, 2)).thenReturn(new ProductStockInfo(1L, "Racao", 79.90));

            mockMvc.perform(post("/api/v1/cart/items")
                            .with(customerAuth(tokenCustomerId))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(addItemRequest(bodyCustomerId, 1L, 2))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId", is(tokenCustomerId.toString())))
                    .andExpect(jsonPath("$.items[0].reserved", is(true)));
        }

        @Test
        void addsItemForRequestedCustomerWhenCalledByStaff() throws Exception {
            var customerId = UUID.randomUUID();
            when(productStockPortOut.reserve(1L, 1)).thenReturn(new ProductStockInfo(1L, "Racao", 79.90));

            mockMvc.perform(post("/api/v1/cart/items")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(addItemRequest(customerId, 1L, 1))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId", is(customerId.toString())));
        }

        @Test
        void returnsBadRequestForInvalidBody() throws Exception {
            var invalid = new AddCartItemRequestDto();

            mockMvc.perform(post("/api/v1/cart/items")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray());
        }
    }

    @Nested
    class RemoveItem {

        @Test
        void removesAPreviouslyAddedItem() throws Exception {
            var customerId = UUID.randomUUID();
            when(productStockPortOut.getInfo(1L)).thenReturn(new ProductStockInfo(1L, "Racao", 79.90));

            mockMvc.perform(post("/api/v1/cart/items")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(addItemRequest(customerId, 1L, 2))));

            mockMvc.perform(delete("/api/v1/cart/items/1").param("customerId", customerId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()", is(0)));
        }

        @Test
        void returnsNotFoundWhenThereIsNoOpenCart() throws Exception {
            mockMvc.perform(delete("/api/v1/cart/items/1").param("customerId", UUID.randomUUID().toString()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class GetCart {

        @Test
        void returnsAFreshEmptyCartWhenNoneOpen() throws Exception {
            mockMvc.perform(get("/api/v1/cart").param("customerId", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()", is(0)))
                    .andExpect(jsonPath("$.status", is("OPEN")));
        }

        @Test
        void returnsExistingCartWithItems() throws Exception {
            var customerId = UUID.randomUUID();
            when(productStockPortOut.getInfo(1L)).thenReturn(new ProductStockInfo(1L, "Racao", 79.90));

            mockMvc.perform(post("/api/v1/cart/items")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(addItemRequest(customerId, 1L, 2))));

            mockMvc.perform(get("/api/v1/cart").param("customerId", customerId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()", is(1)));
        }
    }
}
