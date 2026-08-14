package com.petshop.product_registration.core.application.service;

import com.petshop.product_registration.core.domain.Product;
import com.petshop.product_registration.core.port.in.dto.ProductRequestDto;
import com.petshop.product_registration.core.port.out.ProductPortOut;
import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductPortOut productPortOut;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productPortOut);
    }

    private ProductRequestDto requestDto(String name, Integer stock) {
        var dto = new ProductRequestDto();
        dto.setName(name);
        dto.setPrice(10.0);
        dto.setStock(stock);
        return dto;
    }

    private Product productWithStock(Long id, int stock, int reservedStock) {
        return new Product(id, "Produto", "Desc", 10.0, stock, reservedStock, null, true);
    }

    @Nested
    class RegisterProduct {

        @Test
        void savesProductWithProvidedStock() {
            var request = requestDto("Racao", 50);
            when(productPortOut.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = productService.registerProduct(request);

            assertThat(result.getStock()).isEqualTo(50);
            assertThat(result.getName()).isEqualTo("Racao");
        }

        @Test
        void defaultsStockToZeroWhenNotProvided() {
            var request = requestDto("Racao", null);
            when(productPortOut.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = productService.registerProduct(request);

            assertThat(result.getStock()).isZero();
            assertThat(request.getStock()).isZero();
        }
    }

    @Nested
    class ListProducts {

        @Test
        void returnsOnlyEnabledProducts() {
            when(productPortOut.findByEnabledTrue()).thenReturn(List.of(productWithStock(1L, 10, 0)));

            assertThat(productService.listProducts()).hasSize(1);
        }

        @Test
        void returnsEmptyListWhenNoneEnabled() {
            when(productPortOut.findByEnabledTrue()).thenReturn(List.of());

            assertThat(productService.listProducts()).isEmpty();
        }
    }

    @Nested
    class GetProductById {

        @Test
        void returnsProductWhenFound() {
            var product = productWithStock(1L, 10, 0);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));

            assertThat(productService.getProductById(1L)).isEqualTo(product);
        }

        @Test
        void throwsNotFoundWhenMissing() {
            when(productPortOut.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductById(99L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    @Nested
    class UpdateProduct {

        @Test
        void updatesAndSavesWhenProductExistsAndRequestIsValid() {
            var product = productWithStock(1L, 10, 0);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));
            when(productPortOut.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var update = requestDto("Novo Nome", 20);
            var result = productService.updateProduct(1L, update);

            assertThat(result.getName()).isEqualTo("Novo Nome");
            assertThat(result.getStock()).isEqualTo(20);
        }

        @Test
        void throwsIllegalArgumentWhenRequestIsNull() {
            assertThatThrownBy(() -> productService.updateProduct(1L, null))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(productPortOut);
        }

        @Test
        void throwsNotFoundWhenProductDoesNotExist() {
            when(productPortOut.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(99L, requestDto("Nome", 1)))
                    .isInstanceOf(NotFoundException.class);

            verify(productPortOut, never()).save(any());
        }
    }

    @Nested
    class DeleteProduct {

        @Test
        void disablesProductWhenFound() {
            var product = productWithStock(1L, 10, 0);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));
            when(productPortOut.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = productService.deleteProduct(1L);

            assertThat(result.getEnabled()).isFalse();
        }

        @Test
        void throwsNotFoundWhenMissing() {
            when(productPortOut.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.deleteProduct(99L))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class ReserveStock {

        @Test
        void reservesWhenEnoughAvailableStock() {
            var product = productWithStock(1L, 10, 0);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));
            when(productPortOut.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = productService.reserveStock(1L, 5);

            assertThat(result.getReservedStock()).isEqualTo(5);
        }

        @Test
        void reservesExactlyAllAvailableStock() {
            var product = productWithStock(1L, 10, 0);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));
            when(productPortOut.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = productService.reserveStock(1L, 10);

            assertThat(result.getReservedStock()).isEqualTo(10);
        }

        @Test
        void throwsBusinessRuleWhenNotEnoughStock() {
            var product = productWithStock(1L, 10, 8);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));

            assertThatThrownBy(() -> productService.reserveStock(1L, 5))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Estoque insuficiente");

            verify(productPortOut, never()).save(any());
        }

        @Test
        void throwsNotFoundWhenProductMissing() {
            when(productPortOut.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.reserveStock(99L, 1))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class ReleaseStock {

        @Test
        void releasesReservedQuantity() {
            var product = productWithStock(1L, 10, 5);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));
            when(productPortOut.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = productService.releaseStock(1L, 3);

            assertThat(result.getReservedStock()).isEqualTo(2);
        }

        @Test
        void clampsAtZeroWhenReleasingMoreThanReserved() {
            var product = productWithStock(1L, 10, 2);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));
            when(productPortOut.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = productService.releaseStock(1L, 10);

            assertThat(result.getReservedStock()).isZero();
        }

        @Test
        void throwsNotFoundWhenProductMissing() {
            when(productPortOut.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.releaseStock(99L, 1))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class ConfirmStock {

        @Test
        void confirmsSaleReducingStockAndReservedStock() {
            var product = productWithStock(1L, 10, 5);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));
            when(productPortOut.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = productService.confirmStock(1L, 4);

            assertThat(result.getStock()).isEqualTo(6);
            assertThat(result.getReservedStock()).isEqualTo(1);
        }

        @Test
        void throwsNotFoundWhenProductMissing() {
            when(productPortOut.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.confirmStock(99L, 1))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        void savesTheExactProductInstanceThatWasMutated() {
            var product = productWithStock(1L, 10, 5);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));
            when(productPortOut.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

            productService.confirmStock(1L, 1);

            ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
            verify(productPortOut).save(captor.capture());
            assertThat(captor.getValue()).isSameAs(product);
        }
    }
}
