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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes ponta a ponta: MockMvc + SecurityFilterChain reais + CartService
 * real com H2 em memória; só Feign e Kafka são mockados. /api/v1/cart/**
 * é público, então o foco é validação e o comportamento sem-login vs.
 * logado (resolução do customerId a partir do token) e a disponibilidade
 * por item — o carrinho nunca reserva estoque.
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

    private void productOnSale(Long id, String stockRange) {
        var info = new ProductStockInfo(id, "Produto " + id, 79.90, true, stockRange);
        when(productStockPortOut.getInfo(eq(id), any())).thenReturn(info);
        when(productStockPortOut.getInfos(any(), any())).thenReturn(Map.of(id, info));
    }

    @Nested
    class AddItem {

        @Test
        void addsItemWithoutReservingStockWhenAnonymous() throws Exception {
            var customerId = UUID.randomUUID();
            productOnSale(1L, "50+");

            mockMvc.perform(post("/api/v1/cart/items")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(addItemRequest(customerId, 1L, 2))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId", is(customerId.toString())))
                    .andExpect(jsonPath("$.items[0].availability", is("AVAILABLE")))
                    .andExpect(jsonPath("$.items[0].reserved").doesNotExist());

            verify(productStockPortOut, never()).reserve(any(), anyInt(), any());
        }

        @Test
        void addsItemWithoutReservingStockUsingTokenOwnerAsCustomer() throws Exception {
            var tokenCustomerId = UUID.randomUUID();
            var bodyCustomerId = UUID.randomUUID();
            productOnSale(1L, "LOW");

            mockMvc.perform(post("/api/v1/cart/items")
                            .with(customerAuth(tokenCustomerId))
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(addItemRequest(bodyCustomerId, 1L, 2))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.customerId", is(tokenCustomerId.toString())))
                    .andExpect(jsonPath("$.items[0].availability", is("LOW")));

            verify(productStockPortOut, never()).reserve(any(), anyInt(), any());
        }

        @Test
        void returns422WhenProductHasNoStock() throws Exception {
            when(productStockPortOut.getInfo(eq(1L), any()))
                    .thenReturn(new ProductStockInfo(1L, "Racao", 79.90, true, null));

            mockMvc.perform(post("/api/v1/cart/items")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(addItemRequest(UUID.randomUUID(), 1L, 1))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.detail", is("Produto sem estoque")));
        }

        @Test
        void returns404WhenProductIsDisabled() throws Exception {
            when(productStockPortOut.getInfo(eq(1L), any()))
                    .thenReturn(new ProductStockInfo(1L, "Racao", 79.90, false, "50+"));

            mockMvc.perform(post("/api/v1/cart/items")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(addItemRequest(UUID.randomUUID(), 1L, 1))))
                    .andExpect(status().isNotFound());
        }

        @Test
        void addsItemForRequestedCustomerWhenCalledByStaff() throws Exception {
            var customerId = UUID.randomUUID();
            productOnSale(1L, "50+");

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
            productOnSale(1L, "50+");

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
            productOnSale(1L, "50+");

            mockMvc.perform(post("/api/v1/cart/items")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(addItemRequest(customerId, 1L, 2))));

            mockMvc.perform(get("/api/v1/cart").param("customerId", customerId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()", is(1)));
        }

        @Test
        void keepsOutOfStockItemGreyedOutAndOutOfTheTotal() throws Exception {
            var customerId = UUID.randomUUID();
            productOnSale(1L, "50+");
            mockMvc.perform(post("/api/v1/cart/items")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(addItemRequest(customerId, 1L, 2))));

            when(productStockPortOut.getInfos(any(), any()))
                    .thenReturn(Map.of(1L, new ProductStockInfo(1L, "Produto 1", 79.90, true, null)));

            mockMvc.perform(get("/api/v1/cart").param("customerId", customerId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()", is(1)))
                    .andExpect(jsonPath("$.items[0].availability", is("UNAVAILABLE")))
                    .andExpect(jsonPath("$.total", is(0.0)));
        }

        @Test
        void removesDisabledProductAndKeepsItRemovedOnNextRead() throws Exception {
            var customerId = UUID.randomUUID();
            productOnSale(1L, "50+");
            mockMvc.perform(post("/api/v1/cart/items")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(addItemRequest(customerId, 1L, 2))));

            when(productStockPortOut.getInfos(any(), any()))
                    .thenReturn(Map.of(1L, new ProductStockInfo(1L, "Produto 1", 79.90, false, "50+")));

            mockMvc.perform(get("/api/v1/cart").param("customerId", customerId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()", is(0)))
                    .andExpect(jsonPath("$.removedItems[0]", is("Produto 1")));

            mockMvc.perform(get("/api/v1/cart").param("customerId", customerId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.items.length()", is(0)))
                    .andExpect(jsonPath("$.removedItems.length()", is(0)));
        }

        @Test
        void returns400WhenCustomerIdIsMissing() throws Exception {
            mockMvc.perform(get("/api/v1/cart"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", containsString("customerId")));
        }

        @Test
        void returns400WithoutEchoingValueWhenCustomerIdIsMalformed() throws Exception {
            mockMvc.perform(get("/api/v1/cart").param("customerId", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", containsString("customerId")))
                    .andExpect(jsonPath("$.detail", not(containsString("abc"))));
        }
    }

    /**
     * Erros de protocolo HTTP que o fallback genérico do BaseExceptionHandler
     * transformava em 500.
     */
    @Nested
    class ProtocolErrors {

        @Test
        void returns405WithAllowHeaderForUnsupportedMethod() throws Exception {
            mockMvc.perform(put("/api/v1/cart"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", containsString("GET")))
                    .andExpect(jsonPath("$.title", is("Method not allowed")));
        }

        @Test
        void returns415ForUnsupportedContentType() throws Exception {
            mockMvc.perform(post("/api/v1/cart/items")
                            .contentType("text/plain")
                            .content("qualquer coisa"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.title", is("Unsupported media type")));
        }

        @Test
        void returns404ForUnknownRoute() throws Exception {
            mockMvc.perform(get("/api/v1/cart/rota-inexistente/abc"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource not found")))
                    .andExpect(jsonPath("$.detail", not(containsString("rota-inexistente"))));
        }
    }
}
