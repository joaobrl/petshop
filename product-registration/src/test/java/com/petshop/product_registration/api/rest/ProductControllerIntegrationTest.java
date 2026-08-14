package com.petshop.product_registration.api.rest;

import com.petshop.product_registration.core.port.in.dto.ProductRequestDto;
import com.petshop.product_registration.core.port.in.dto.StockAdjustmentRequestDto;
import com.petshop.product_registration.infrastructure.persistence.mongo.entity.ProductDocument;
import com.petshop.product_registration.infrastructure.persistence.mongo.repository.ProductMongoRepository;
import com.petshop.product_registration.infrastructure.persistence.mongo.sequence.ProductSequenceGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ponta a ponta: MockMvc real com SecurityFilterChain/JwtAuthenticationFilter
 * de verdade, e ProductPortIn/ProductService/ProductMapper reais — só o
 * ProductMongoRepository é mockado (por isso os mocks usam ProductDocument,
 * não Product). Tokens são gerados de verdade (mesmo segredo do
 * application.yml de teste) via header Authorization.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    // Mesmo segredo do src/test/resources/application.yml.
    private static final JwtService TEST_JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ProductMongoRepository productRepository;

    @MockitoBean
    private ProductSequenceGenerator sequenceGenerator;

    private static RequestPostProcessor adminAuth() {
        return bearer(TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "admin@petshop.local", "Admin", "00000000000", "11000000000", Role.ADMIN, AccountType.STAFF));
    }

    private static RequestPostProcessor customerAuth() {
        return bearer(TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER));
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

    private ProductDocument sampleProduct(Long id) {
        var entity = new ProductDocument();
        entity.setId(id);
        entity.setName("Racao");
        entity.setDescription("Descricao");
        entity.setPrice(79.90);
        entity.setStock(50);
        entity.setReservedStock(0);
        entity.setEnabled(true);
        return entity;
    }

    private ProductRequestDto validRequest() {
        var dto = new ProductRequestDto();
        dto.setName("Racao");
        dto.setPrice(79.90);
        dto.setStock(50);
        return dto;
    }

    @Nested
    class PublicCatalog {

        @Test
        void listProductsIsPubliclyAccessible() throws Exception {
            when(productRepository.findByEnabledTrue()).thenReturn(List.of(sampleProduct(1L)));

            mockMvc.perform(get("/api/v1/products/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()", is(1)));
        }

        @Test
        void listProductsOmitsStockWhenAnonymous() throws Exception {
            when(productRepository.findByEnabledTrue()).thenReturn(List.of(sampleProduct(1L)));

            mockMvc.perform(get("/api/v1/products/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].stock").doesNotExist())
                    .andExpect(jsonPath("$[0].reservedPercentage").doesNotExist());
        }

        @Test
        void listProductsOmitsStockWhenCustomerToken() throws Exception {
            when(productRepository.findByEnabledTrue()).thenReturn(List.of(sampleProduct(1L)));

            mockMvc.perform(get("/api/v1/products/list").with(customerAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].stock").doesNotExist());
        }

        @Test
        void listProductsIncludesStockWhenStaffToken() throws Exception {
            when(productRepository.findByEnabledTrue()).thenReturn(List.of(sampleProduct(1L)));

            mockMvc.perform(get("/api/v1/products/list").with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].stock", is(50)))
                    .andExpect(jsonPath("$[0].reservedPercentage", is(0.0)));
        }

        @Test
        void findProductByIdIsPubliclyAccessible() throws Exception {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct(1L)));

            mockMvc.perform(get("/api/v1/products/find/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Racao")));
        }

        @Test
        void findProductByIdOmitsStockWhenAnonymous() throws Exception {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct(1L)));

            mockMvc.perform(get("/api/v1/products/find/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stock").doesNotExist());
        }

        @Test
        void findProductByIdOmitsStockWhenCustomerToken() throws Exception {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct(1L)));

            mockMvc.perform(get("/api/v1/products/find/1").with(customerAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stock").doesNotExist());
        }

        @Test
        void findProductByIdIncludesStockWhenStaffToken() throws Exception {
            when(productRepository.findById(1L)).thenReturn(Optional.of(sampleProduct(1L)));

            mockMvc.perform(get("/api/v1/products/find/1").with(adminAuth()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stock", is(50)))
                    .andExpect(jsonPath("$.reservedPercentage", is(0.0)));
        }

        @Test
        void findProductByIdReturnsNotFoundProblemDetailWhenMissing() throws Exception {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/products/find/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title", is("Resource not found")));
        }
    }

    @Nested
    class AdminOnlyManagement {

        @Test
        void registerProductRequiresAuthentication() throws Exception {
            mockMvc.perform(post("/api/v1/products/register")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void registerProductForbiddenForNonAdminRole() throws Exception {
            mockMvc.perform(post("/api/v1/products/register")
                            .with(customerAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void registerProductSucceedsForAdmin() throws Exception {
            when(sequenceGenerator.generateSequence(any())).thenReturn(1L);
            when(productRepository.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(post("/api/v1/products/register")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name", is("Racao")));
        }

        @Test
        void registerProductReturnsBadRequestForInvalidBody() throws Exception {
            var invalid = new ProductRequestDto();
            invalid.setName("");
            invalid.setPrice(-1.0);

            mockMvc.perform(post("/api/v1/products/register")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors").isArray());
        }

        @Test
        void registerProductMapsDuplicateNameToInternalServerErrorProblemDetail() throws Exception {
            when(productRepository.save(any(ProductDocument.class))).thenThrow(new DataIntegrityViolationException("duplicate"));

            mockMvc.perform(post("/api/v1/products/register")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.title", is("Internal server error")));
        }

        @Test
        void updateProductRequiresAdminRole() throws Exception {
            mockMvc.perform(patch("/api/v1/products/update/1")
                            .with(customerAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        void updateProductSucceedsForAdmin() throws Exception {
            var product = sampleProduct(1L);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

            var update = new ProductRequestDto();
            update.setName("Nome Atualizado");

            mockMvc.perform(patch("/api/v1/products/update/1")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name", is("Nome Atualizado")));
        }

        @Test
        void updateProductReturnsNotFoundProblemDetailWhenMissing() throws Exception {
            when(productRepository.findById(99L)).thenReturn(Optional.empty());

            mockMvc.perform(patch("/api/v1/products/update/99")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        void deleteProductRequiresAdminRole() throws Exception {
            mockMvc.perform(delete("/api/v1/products/delete/1")
                            .with(customerAuth()))
                    .andExpect(status().isForbidden());
        }

        @Test
        void deleteProductSucceedsForAdmin() throws Exception {
            var product = sampleProduct(1L);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(delete("/api/v1/products/delete/1")
                            .with(adminAuth()))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    class StockAdjustmentEndpoints {

        private StockAdjustmentRequestDto quantity(int q) {
            var dto = new StockAdjustmentRequestDto();
            dto.setQuantity(q);
            return dto;
        }

        @Test
        void reserveStockRequiresAuthenticationButNotAnySpecificRole() throws Exception {
            mockMvc.perform(post("/api/v1/products/1/reserve")
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(quantity(5))))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void reserveStockSucceedsForAnyAuthenticatedRole() throws Exception {
            var product = sampleProduct(1L);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(post("/api/v1/products/1/reserve")
                            .with(customerAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(quantity(5))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservedStock", is(5)));
        }

        @Test
        void reserveStockReturnsUnprocessableEntityWhenInsufficientStock() throws Exception {
            var product = sampleProduct(1L);
            product.setStock(5);
            product.setReservedStock(5);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));

            mockMvc.perform(post("/api/v1/products/1/reserve")
                            .with(customerAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(quantity(1))))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.title", is("Business rule violation")));
        }

        @Test
        void reserveStockReturnsBadRequestForNonPositiveQuantity() throws Exception {
            mockMvc.perform(post("/api/v1/products/1/reserve")
                            .with(customerAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(quantity(0))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void releaseStockSucceedsForAnyAuthenticatedRole() throws Exception {
            var product = sampleProduct(1L);
            product.setReservedStock(5);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(post("/api/v1/products/1/release")
                            .with(receptionistAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(quantity(3))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.reservedStock", is(2)));
        }

        @Test
        void confirmStockSucceedsForAnyAuthenticatedRole() throws Exception {
            var product = sampleProduct(1L);
            product.setReservedStock(5);
            when(productRepository.findById(1L)).thenReturn(Optional.of(product));
            when(productRepository.save(any(ProductDocument.class))).thenAnswer(inv -> inv.getArgument(0));

            mockMvc.perform(post("/api/v1/products/1/confirm")
                            .with(adminAuth())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(quantity(4))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.stock", is(46)));
        }
    }
}
