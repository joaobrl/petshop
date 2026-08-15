package com.petshop.product_registration.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.product_registration.core.domain.Product;
import com.petshop.product_registration.core.port.in.ProductPortIn;
import com.petshop.product_registration.core.port.in.dto.ProductRequestDto;
import com.petshop.product_registration.core.port.in.dto.StockAdjustmentRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerImplTest {

    @Mock
    private ProductPortIn portIn;

    private ProductControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new ProductControllerImpl(portIn);
    }

    private Product sampleProduct() {
        return new Product(1L, "Racao", "Descricao", 79.90, 50, 0, null, true);
    }

    private AuthenticatedUser staffUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "joao@petshop.local", "Joao", "12345678900", "11999999999", Role.RECEPTIONIST, AccountType.STAFF);
    }

    private AuthenticatedUser customerUser() {
        return new AuthenticatedUser(UUID.randomUUID(), "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER);
    }

    @Test
    void registerProductReturnsCreatedWithLocationAndBody() {
        var product = sampleProduct();
        var request = new ProductRequestDto();
        request.setName("Racao");
        request.setPrice(79.90);
        when(portIn.registerProduct(request)).thenReturn(product);

        var response = controller.registerProduct(request,
                UriComponentsBuilder.fromUriString("http://localhost:8080/api/v1/products/register"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        assertThat(response.getHeaders().getLocation().toString()).contains("/products/1");
    }

    private final PageRequest pageable = PageRequest.of(0, 20);

    @Test
    void listProductsMapsAllProductsToDto() {
        when(portIn.listProducts(pageable)).thenReturn(new PageImpl<>(List.of(sampleProduct(), sampleProduct())));

        var response = controller.listProducts(pageable, staffUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().content()).hasSize(2);
    }

    @Test
    void listProductsReturnsEmptyListWhenNoneEnabled() {
        when(portIn.listProducts(pageable)).thenReturn(new PageImpl<>(List.of()));

        assertThat(controller.listProducts(pageable, staffUser()).getBody().content()).isEmpty();
    }

    @Test
    void listProductsIncludesStockWhenUserIsStaff() {
        when(portIn.listProducts(pageable)).thenReturn(new PageImpl<>(List.of(sampleProduct())));

        var response = controller.listProducts(pageable, staffUser());

        assertThat(response.getBody().content().get(0).getStock()).isEqualTo(50);
        assertThat(response.getBody().content().get(0).getReservedPercentage()).isNotNull();
    }

    @Test
    void listProductsOmitsStockWhenUserIsCustomer() {
        when(portIn.listProducts(pageable)).thenReturn(new PageImpl<>(List.of(sampleProduct())));

        var response = controller.listProducts(pageable, customerUser());

        assertThat(response.getBody().content().get(0).getStock()).isNull();
        assertThat(response.getBody().content().get(0).getReservedPercentage()).isNull();
    }

    @Test
    void listProductsOmitsStockWhenAnonymous() {
        when(portIn.listProducts(pageable)).thenReturn(new PageImpl<>(List.of(sampleProduct())));

        var response = controller.listProducts(pageable, null);

        assertThat(response.getBody().content().get(0).getStock()).isNull();
    }

    @Test
    void getProductByIdDelegatesToPortIn() {
        var product = sampleProduct();
        when(portIn.getProductById(1L)).thenReturn(product);

        var response = controller.getProductById(1L, staffUser());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(1L);
    }

    @Test
    void getProductByIdIncludesStockWhenUserIsStaff() {
        when(portIn.getProductById(1L)).thenReturn(sampleProduct());

        var response = controller.getProductById(1L, staffUser());

        assertThat(response.getBody().getStock()).isEqualTo(50);
        assertThat(response.getBody().getReservedPercentage()).isNotNull();
    }

    @Test
    void getProductByIdOmitsStockWhenUserIsCustomer() {
        when(portIn.getProductById(1L)).thenReturn(sampleProduct());

        var response = controller.getProductById(1L, customerUser());

        assertThat(response.getBody().getStock()).isNull();
    }

    @Test
    void getProductByIdOmitsStockWhenAnonymous() {
        when(portIn.getProductById(1L)).thenReturn(sampleProduct());

        var response = controller.getProductById(1L, null);

        assertThat(response.getBody().getStock()).isNull();
    }

    @Test
    void updateProductDelegatesToPortInAndReturnsUpdatedBody() {
        var product = sampleProduct();
        var request = new ProductRequestDto();
        request.setName("Novo Nome");
        when(portIn.updateProduct(1L, request)).thenReturn(product);

        var response = controller.updateProduct(1L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(1L);
    }

    @Test
    void deleteProductReturnsNoContent() {
        when(portIn.deleteProduct(1L)).thenReturn(sampleProduct());

        var response = controller.deleteProduct(1L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(portIn).deleteProduct(1L);
    }

    @Nested
    class StockAdjustments {

        private StockAdjustmentRequestDto quantity(int q) {
            var dto = new StockAdjustmentRequestDto();
            dto.setQuantity(q);
            return dto;
        }

        @Test
        void reserveStockDelegatesToPortInWithRequestedQuantity() {
            var product = sampleProduct();
            when(portIn.reserveStock(1L, 5)).thenReturn(product);

            var response = controller.reserveStock(1L, quantity(5));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(portIn).reserveStock(1L, 5);
        }

        @Test
        void releaseStockDelegatesToPortInWithRequestedQuantity() {
            var product = sampleProduct();
            when(portIn.releaseStock(1L, 3)).thenReturn(product);

            var response = controller.releaseStock(1L, quantity(3));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(portIn).releaseStock(1L, 3);
        }

        @Test
        void confirmStockDelegatesToPortInWithRequestedQuantity() {
            var product = sampleProduct();
            when(portIn.confirmStock(1L, 2)).thenReturn(product);

            var response = controller.confirmStock(1L, quantity(2));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(portIn).confirmStock(1L, 2);
        }
    }
}
