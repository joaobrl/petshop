package com.petshop.order_service.infrastructure.rest.product.feign.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockAdjustmentRequestDtoTest {

    @Test
    void exposesQuantityViaAccessor() {
        var dto = new StockAdjustmentRequestDto(5);

        assertThat(dto.quantity()).isEqualTo(5);
    }

    @Test
    void equalsAndHashCodeConsiderTheQuantity() {
        var a = new StockAdjustmentRequestDto(5);
        var b = new StockAdjustmentRequestDto(5);
        var c = new StockAdjustmentRequestDto(6);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("5");
    }
}
