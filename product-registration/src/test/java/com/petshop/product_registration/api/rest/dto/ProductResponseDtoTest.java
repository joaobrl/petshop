package com.petshop.product_registration.api.rest.dto;

import com.petshop.product_registration.core.domain.Dimensions;
import com.petshop.product_registration.core.domain.Product;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductResponseDtoTest {

    @Test
    void copiesAllScalarFieldsAndComputesAvailableStock() {
        var product = new Product(1L, "Racao", "Descricao", 79.90, 50, 10, null, true);

        var dto = new ProductResponseDto(product);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Racao");
        assertThat(dto.getDescription()).isEqualTo("Descricao");
        assertThat(dto.getPrice()).isEqualTo(79.90);
        assertThat(dto.getStock()).isEqualTo(50);
        assertThat(dto.getReservedStock()).isEqualTo(10);
        assertThat(dto.getAvailableStock()).isEqualTo(40);
        assertThat(dto.getReservedPercentage()).isEqualTo(20.0);
    }

    @Test
    void mapsDimensionsWhenPresent() {
        var dimensions = new Dimensions(1.5, 20.0, 15.0, 30.0);
        var product = new Product(1L, "Racao", "Descricao", 79.90, 50, 0, dimensions, true);

        var dto = new ProductResponseDto(product);

        assertThat(dto.getDimensions()).isNotNull();
        assertThat(dto.getDimensions().getWeight()).isEqualTo(1.5);
        assertThat(dto.getDimensions().getHeight()).isEqualTo(20.0);
        assertThat(dto.getDimensions().getWidth()).isEqualTo(15.0);
        assertThat(dto.getDimensions().getLength()).isEqualTo(30.0);
    }

    @Test
    void leavesDimensionsNullWhenProductHasNone() {
        var product = new Product(1L, "Racao", "Descricao", 79.90, 50, 0, null, true);

        var dto = new ProductResponseDto(product);

        assertThat(dto.getDimensions()).isNull();
    }

    @Test
    void availableStockIsZeroWhenFullyReserved() {
        var product = new Product(1L, "Racao", "Descricao", 79.90, 10, 10, null, true);

        var dto = new ProductResponseDto(product);

        assertThat(dto.getAvailableStock()).isZero();
    }

    @Test
    void includesStockFieldsWhenIncludeStockIsTrue() {
        var product = new Product(1L, "Racao", "Descricao", 79.90, 50, 10, null, true);

        var dto = new ProductResponseDto(product, true);

        assertThat(dto.getStock()).isEqualTo(50);
        assertThat(dto.getReservedStock()).isEqualTo(10);
        assertThat(dto.getAvailableStock()).isEqualTo(40);
        assertThat(dto.getReservedPercentage()).isEqualTo(20.0);
    }

    @Test
    void omitsStockFieldsWhenIncludeStockIsFalse() {
        var product = new Product(1L, "Racao", "Descricao", 79.90, 50, 10, null, true);

        var dto = new ProductResponseDto(product, false);

        assertThat(dto.getStock()).isNull();
        assertThat(dto.getReservedStock()).isNull();
        assertThat(dto.getAvailableStock()).isNull();
        assertThat(dto.getReservedPercentage()).isNull();
        assertThat(dto.getName()).isEqualTo("Racao");
        assertThat(dto.getPrice()).isEqualTo(79.90);
    }
}
