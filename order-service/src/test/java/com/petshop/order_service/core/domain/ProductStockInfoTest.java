package com.petshop.order_service.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductStockInfoTest {

    @Test
    void exposesAllFieldsViaAccessors() {
        var info = new ProductStockInfo(1L, "Racao", 79.90);

        assertThat(info.id()).isEqualTo(1L);
        assertThat(info.name()).isEqualTo("Racao");
        assertThat(info.price()).isEqualTo(79.90);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var a = new ProductStockInfo(1L, "Racao", 79.90);
        var b = new ProductStockInfo(1L, "Racao", 79.90);
        var c = new ProductStockInfo(2L, "Racao", 79.90);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("Racao");
    }
}
