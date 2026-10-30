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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

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

        private final PageRequest pageable = PageRequest.of(0, 20);

        @Test
        void returnsOnlyEnabledProducts() {
            when(productPortOut.findByEnabledTrue(pageable))
                    .thenReturn(new PageImpl<>(List.of(productWithStock(1L, 10, 0))));

            assertThat(productService.listProducts(pageable).getContent()).hasSize(1);
        }

        @Test
        void returnsEmptyListWhenNoneEnabled() {
            when(productPortOut.findByEnabledTrue(pageable)).thenReturn(new PageImpl<>(List.of()));

            assertThat(productService.listProducts(pageable).getContent()).isEmpty();
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
    class GetProductsByIds {

        @Test
        @SuppressWarnings("unchecked")
        void deduplicatesIdsAndQueriesOnce() {
            var products = List.of(productWithStock(1L, 10, 0), productWithStock(2L, 0, 0));
            when(productPortOut.findAllByIds(any())).thenReturn(products);

            var result = productService.getProductsByIds(List.of(1L, 2L, 1L));

            assertThat(result).isEqualTo(products);
            ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(productPortOut, times(1)).findAllByIds(captor.capture());
            assertThat(captor.getValue()).containsExactly(1L, 2L);
        }

        @Test
        void throwsIllegalArgumentWhenEmpty() {
            assertThatThrownBy(() -> productService.getProductsByIds(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(productPortOut);
        }

        @Test
        void throwsIllegalArgumentWhenAboveLimit() {
            var ids = LongStream.rangeClosed(1, 101).boxed().toList();

            assertThatThrownBy(() -> productService.getProductsByIds(ids))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("100");

            verifyNoInteractions(productPortOut);
        }

        @Test
        void acceptsExactlyTheLimit() {
            var ids = LongStream.rangeClosed(1, 100).boxed().toList();
            when(productPortOut.findAllByIds(any())).thenReturn(List.of());

            assertThat(productService.getProductsByIds(ids)).isEmpty();
        }
    }

    @Nested
    class UpdateProduct {

        @Test
        void updatesOnlyEditableFieldsWhenProductExistsAndRequestIsValid() {
            var product = productWithStock(1L, 10, 0);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));
            when(productPortOut.updateDetails(any(Product.class))).thenAnswer(inv -> Optional.of(inv.getArgument(0)));

            var update = requestDto("Novo Nome", 20);
            var result = productService.updateProduct(1L, update);

            assertThat(result.getName()).isEqualTo("Novo Nome");
            assertThat(result.getStock()).isEqualTo(20);
            verify(productPortOut, never()).save(any());
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

            verify(productPortOut, never()).updateDetails(any());
        }

        @Test
        void throwsNotFoundWhenProductVanishesBeforeUpdate() {
            when(productPortOut.findById(1L)).thenReturn(Optional.of(productWithStock(1L, 10, 0)));
            when(productPortOut.updateDetails(any(Product.class))).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.updateProduct(1L, requestDto("Nome", 1)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class DeleteProduct {

        @Test
        void disablesProductWhenFound() {
            var product = productWithStock(1L, 10, 0);
            when(productPortOut.findById(1L)).thenReturn(Optional.of(product));
            when(productPortOut.updateDetails(any(Product.class))).thenAnswer(inv -> Optional.of(inv.getArgument(0)));

            var result = productService.deleteProduct(1L);

            assertThat(result.getEnabled()).isFalse();
            verify(productPortOut, never()).save(any());
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
        void returnsProductUpdatedByTheAtomicReserve() {
            var reserved = productWithStock(1L, 10, 5);
            when(productPortOut.tryReserve(1L, 5)).thenReturn(Optional.of(reserved));

            var result = productService.reserveStock(1L, 5);

            assertThat(result.getReservedStock()).isEqualTo(5);
            verify(productPortOut, never()).save(any());
            verify(productPortOut, never()).findById(any());
        }

        @Test
        void throwsBusinessRuleWithoutQuantityWhenNotEnoughStock() {
            when(productPortOut.tryReserve(1L, 5)).thenReturn(Optional.empty());
            when(productPortOut.findById(1L)).thenReturn(Optional.of(productWithStock(1L, 10, 8)));

            assertThatThrownBy(() -> productService.reserveStock(1L, 5))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Estoque insuficiente para o produto 'Produto'");
        }

        @Test
        void throwsBusinessRuleWhenProductIsDisabled() {
            var disabled = new Product(1L, "Produto", "Desc", 10.0, 10, 0, null, false);
            when(productPortOut.tryReserve(1L, 1)).thenReturn(Optional.empty());
            when(productPortOut.findById(1L)).thenReturn(Optional.of(disabled));

            assertThatThrownBy(() -> productService.reserveStock(1L, 1))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Produto indisponível");
        }

        @Test
        void throwsNotFoundWhenProductMissing() {
            when(productPortOut.tryReserve(99L, 1)).thenReturn(Optional.empty());
            when(productPortOut.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.reserveStock(99L, 1))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class ReleaseStock {

        @Test
        void returnsProductUpdatedByTheAtomicRelease() {
            when(productPortOut.tryRelease(1L, 3)).thenReturn(Optional.of(productWithStock(1L, 10, 2)));

            var result = productService.releaseStock(1L, 3);

            assertThat(result.getReservedStock()).isEqualTo(2);
            verify(productPortOut, never()).save(any());
        }

        @Test
        void throwsBusinessRuleWhenReleasingMoreThanReserved() {
            when(productPortOut.tryRelease(1L, 10)).thenReturn(Optional.empty());
            when(productPortOut.findById(1L)).thenReturn(Optional.of(productWithStock(1L, 10, 2)));

            assertThatThrownBy(() -> productService.releaseStock(1L, 10))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Reserva insuficiente");
        }

        @Test
        void throwsNotFoundWhenProductMissing() {
            when(productPortOut.tryRelease(99L, 1)).thenReturn(Optional.empty());
            when(productPortOut.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.releaseStock(99L, 1))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class ConfirmStock {

        @Test
        void returnsProductUpdatedByTheAtomicConfirm() {
            when(productPortOut.tryConfirm(1L, 4)).thenReturn(Optional.of(productWithStock(1L, 6, 1)));

            var result = productService.confirmStock(1L, 4);

            assertThat(result.getStock()).isEqualTo(6);
            assertThat(result.getReservedStock()).isEqualTo(1);
            verify(productPortOut, never()).save(any());
        }

        @Test
        void throwsBusinessRuleWhenConfirmingMoreThanReserved() {
            when(productPortOut.tryConfirm(1L, 4)).thenReturn(Optional.empty());
            when(productPortOut.findById(1L)).thenReturn(Optional.of(productWithStock(1L, 10, 1)));

            assertThatThrownBy(() -> productService.confirmStock(1L, 4))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Reserva insuficiente");
        }

        @Test
        void throwsNotFoundWhenProductMissing() {
            when(productPortOut.tryConfirm(99L, 1)).thenReturn(Optional.empty());
            when(productPortOut.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.confirmStock(99L, 1))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
