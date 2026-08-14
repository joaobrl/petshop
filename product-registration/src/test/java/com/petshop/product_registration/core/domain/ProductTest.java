package com.petshop.product_registration.core.domain;

import com.petshop.product_registration.core.port.in.dto.DimensionsDto;
import com.petshop.product_registration.core.port.in.dto.ProductRequestDto;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    private ProductRequestDto requestWithDimensions() {
        var dto = new ProductRequestDto();
        dto.setName("Racao Premium");
        dto.setDescription("Racao para caes adultos");
        dto.setPrice(79.90);
        dto.setStock(50);
        var dimensions = new DimensionsDto();
        dimensions.setWeight(1.5);
        dimensions.setHeight(20.0);
        dimensions.setWidth(15.0);
        dimensions.setLength(30.0);
        dto.setDimensions(dimensions);
        return dto;
    }

    @Nested
    class Construction {

        @Test
        void requestConstructorCopiesAllFieldsAndEnablesProduct() {
            var product = new Product(requestWithDimensions());

            assertThat(product.getName()).isEqualTo("Racao Premium");
            assertThat(product.getDescription()).isEqualTo("Racao para caes adultos");
            assertThat(product.getPrice()).isEqualTo(79.90);
            assertThat(product.getStock()).isEqualTo(50);
            assertThat(product.getReservedStock()).isZero();
            assertThat(product.getEnabled()).isTrue();
            assertThat(product.getDimensions()).isNotNull();
            assertThat(product.getDimensions().getWeight()).isEqualTo(1.5);
            assertThat(product.getDimensions().getHeight()).isEqualTo(20.0);
            assertThat(product.getDimensions().getWidth()).isEqualTo(15.0);
            assertThat(product.getDimensions().getLength()).isEqualTo(30.0);
        }

        @Test
        void requestConstructorLeavesDimensionsNullWhenNotProvided() {
            var dto = new ProductRequestDto();
            dto.setName("Brinquedo");
            dto.setPrice(10.0);
            dto.setStock(5);

            var product = new Product(dto);

            assertThat(product.getDimensions()).isNull();
        }

        @Test
        void noArgsConstructorLeavesFieldsNull() {
            var product = new Product();

            assertThat(product.getId()).isNull();
            assertThat(product.getName()).isNull();
        }

        @Test
        void allArgsConstructorSetsEveryField() {
            var dimensions = new Dimensions(1.0, 2.0, 3.0, 4.0);
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 2, dimensions, true);

            assertThat(product.getId()).isEqualTo(1L);
            assertThat(product.getName()).isEqualTo("Nome");
            assertThat(product.getDescription()).isEqualTo("Desc");
            assertThat(product.getPrice()).isEqualTo(9.99);
            assertThat(product.getStock()).isEqualTo(10);
            assertThat(product.getReservedStock()).isEqualTo(2);
            assertThat(product.getDimensions()).isEqualTo(dimensions);
            assertThat(product.getEnabled()).isTrue();
        }
    }

    @Nested
    class AvailableStock {

        @Test
        void isStockMinusReservedStock() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 3, null, true);

            assertThat(product.availableStock()).isEqualTo(7);
        }

        @Test
        void isEqualToStockWhenNothingReserved() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 0, null, true);

            assertThat(product.availableStock()).isEqualTo(10);
        }
    }

    @Nested
    class ReservedPercentage {

        @Test
        void isReservedStockOverStockTimesHundred() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 3, null, true);

            assertThat(product.reservedPercentage()).isEqualTo(30.0);
        }

        @Test
        void isZeroWhenNothingReserved() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 0, null, true);

            assertThat(product.reservedPercentage()).isZero();
        }

        @Test
        void isHundredWhenEverythingReserved() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 10, null, true);

            assertThat(product.reservedPercentage()).isEqualTo(100.0);
        }

        @Test
        void isZeroWhenStockIsZeroToAvoidDivisionByZero() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 0, 0, null, true);

            assertThat(product.reservedPercentage()).isZero();
        }
    }

    @Nested
    class Reserve {

        @Test
        void increasesReservedStock() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 0, null, true);

            product.reserve(4);

            assertThat(product.getReservedStock()).isEqualTo(4);
            assertThat(product.availableStock()).isEqualTo(6);
        }

        @Test
        void accumulatesAcrossMultipleReserves() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 0, null, true);

            product.reserve(3);
            product.reserve(2);

            assertThat(product.getReservedStock()).isEqualTo(5);
        }
    }

    @Nested
    class Release {

        @Test
        void decreasesReservedStock() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 5, null, true);

            product.release(3);

            assertThat(product.getReservedStock()).isEqualTo(2);
        }

        @Test
        void clampsAtZeroWhenReleasingMoreThanReserved() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 2, null, true);

            product.release(10);

            assertThat(product.getReservedStock()).isZero();
        }

        @Test
        void releasingZeroDoesNotChangeReservedStock() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 5, null, true);

            product.release(0);

            assertThat(product.getReservedStock()).isEqualTo(5);
        }
    }

    @Nested
    class ConfirmSale {

        @Test
        void reducesBothStockAndReservedStock() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 10, 5, null, true);

            product.confirmSale(4);

            assertThat(product.getStock()).isEqualTo(6);
            assertThat(product.getReservedStock()).isEqualTo(1);
        }

        @Test
        void clampsStockAtZeroWhenConfirmingMoreThanAvailable() {
            var product = new Product(1L, "Nome", "Desc", 9.99, 3, 3, null, true);

            product.confirmSale(10);

            assertThat(product.getStock()).isZero();
            assertThat(product.getReservedStock()).isZero();
        }
    }

    @Nested
    class Update {

        @Test
        void updatesAllFieldsWhenAllPresent() {
            var product = new Product(requestWithDimensions());

            var newData = new ProductRequestDto();
            newData.setName("Novo Nome");
            newData.setDescription("Nova Descricao");
            newData.setPrice(99.90);
            newData.setStock(20);
            var newDimensions = new DimensionsDto();
            newDimensions.setWeight(2.0);
            newDimensions.setHeight(25.0);
            newDimensions.setWidth(18.0);
            newDimensions.setLength(35.0);
            newData.setDimensions(newDimensions);

            product.update(newData);

            assertThat(product.getName()).isEqualTo("Novo Nome");
            assertThat(product.getDescription()).isEqualTo("Nova Descricao");
            assertThat(product.getPrice()).isEqualTo(99.90);
            assertThat(product.getStock()).isEqualTo(20);
            assertThat(product.getDimensions().getWeight()).isEqualTo(2.0);
            assertThat(product.getDimensions().getHeight()).isEqualTo(25.0);
            assertThat(product.getDimensions().getWidth()).isEqualTo(18.0);
            assertThat(product.getDimensions().getLength()).isEqualTo(35.0);
        }

        @Test
        void keepsAllFieldsWhenUpdateDtoIsEmpty() {
            var product = new Product(requestWithDimensions());

            product.update(new ProductRequestDto());

            assertThat(product.getName()).isEqualTo("Racao Premium");
            assertThat(product.getDescription()).isEqualTo("Racao para caes adultos");
            assertThat(product.getPrice()).isEqualTo(79.90);
            assertThat(product.getStock()).isEqualTo(50);
            assertThat(product.getDimensions().getWeight()).isEqualTo(1.5);
        }

        @Test
        void updatesOnlyNameWhenOnlyNameProvided() {
            var product = new Product(requestWithDimensions());
            var partial = new ProductRequestDto();
            partial.setName("Só o Nome Mudou");

            product.update(partial);

            assertThat(product.getName()).isEqualTo("Só o Nome Mudou");
            assertThat(product.getPrice()).isEqualTo(79.90);
        }

        @Test
        void createsDimensionsWhenProductHadNoneAndUpdateProvidesThem() {
            var dto = new ProductRequestDto();
            dto.setName("Brinquedo");
            dto.setPrice(10.0);
            dto.setStock(5);
            var product = new Product(dto);
            assertThat(product.getDimensions()).isNull();

            var update = new ProductRequestDto();
            var dimensions = new DimensionsDto();
            dimensions.setWeight(0.5);
            update.setDimensions(dimensions);

            product.update(update);

            assertThat(product.getDimensions()).isNotNull();
            assertThat(product.getDimensions().getWeight()).isEqualTo(0.5);
        }

        @Test
        void updatesOnlyProvidedDimensionFieldsKeepingOthers() {
            var product = new Product(requestWithDimensions());
            var update = new ProductRequestDto();
            var dimensions = new DimensionsDto();
            dimensions.setWeight(3.3);
            update.setDimensions(dimensions);

            product.update(update);

            assertThat(product.getDimensions().getWeight()).isEqualTo(3.3);
            assertThat(product.getDimensions().getHeight()).isEqualTo(20.0);
            assertThat(product.getDimensions().getWidth()).isEqualTo(15.0);
            assertThat(product.getDimensions().getLength()).isEqualTo(30.0);
        }
    }

    @Test
    void equalsAndHashCodeAreBasedOnIdOnly() {
        var product1 = new Product(1L, "A", "descA", 1.0, 1, 0, null, true);
        var product2 = new Product(1L, "B", "descB", 2.0, 2, 1, null, false);
        var product3 = new Product(2L, "A", "descA", 1.0, 1, 0, null, true);

        assertThat(product1).isEqualTo(product2);
        assertThat(product1.hashCode()).isEqualTo(product2.hashCode());
        assertThat(product1).isNotEqualTo(product3);
        assertThat(product1).isNotEqualTo(null);
        assertThat(product1).isNotEqualTo("not a product");
    }

    @Test
    void settersUpdateFieldsDirectly() {
        var product = new Product();
        var dimensions = new Dimensions(1.0, 2.0, 3.0, 4.0);

        product.setId(1L);
        product.setName("Nome");
        product.setDescription("Desc");
        product.setPrice(9.99);
        product.setStock(10);
        product.setReservedStock(2);
        product.setDimensions(dimensions);
        product.setEnabled(true);

        assertThat(product.getId()).isEqualTo(1L);
        assertThat(product.getName()).isEqualTo("Nome");
        assertThat(product.getDescription()).isEqualTo("Desc");
        assertThat(product.getPrice()).isEqualTo(9.99);
        assertThat(product.getStock()).isEqualTo(10);
        assertThat(product.getReservedStock()).isEqualTo(2);
        assertThat(product.getDimensions()).isEqualTo(dimensions);
        assertThat(product.getEnabled()).isTrue();
    }
}
