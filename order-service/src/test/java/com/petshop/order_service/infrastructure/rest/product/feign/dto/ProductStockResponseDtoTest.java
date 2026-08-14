package com.petshop.order_service.infrastructure.rest.product.feign.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductStockResponseDtoTest {

    @Test
    void settersAndGettersRoundTrip() {
        var dto = new ProductStockResponseDto();

        dto.setId(1L);
        dto.setName("Racao");
        dto.setPrice(79.90);
        dto.setStock(50);
        dto.setReservedStock(5);
        dto.setAvailableStock(45);

        assertThat(dto.getId()).isEqualTo(1L);
        assertThat(dto.getName()).isEqualTo("Racao");
        assertThat(dto.getPrice()).isEqualTo(79.90);
        assertThat(dto.getStock()).isEqualTo(50);
        assertThat(dto.getReservedStock()).isEqualTo(5);
        assertThat(dto.getAvailableStock()).isEqualTo(45);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var a = new ProductStockResponseDto();
        a.setId(1L);
        a.setName("Racao");
        var b = new ProductStockResponseDto();
        b.setId(1L);
        b.setName("Racao");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("Racao");
    }
}
