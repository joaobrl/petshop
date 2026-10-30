package com.petshop.order_service.core.domain;

import com.petshop.order_service.core.domain.enums.StockAvailability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ProductStockInfoTest {

    @Test
    void exposesAllFieldsViaAccessors() {
        var info = new ProductStockInfo(1L, "Racao", 79.90, true, "50+");

        assertThat(info.id()).isEqualTo(1L);
        assertThat(info.name()).isEqualTo("Racao");
        assertThat(info.price()).isEqualTo(79.90);
        assertThat(info.enabled()).isTrue();
        assertThat(info.stockRange()).isEqualTo("50+");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var a = new ProductStockInfo(1L, "Racao", 79.90, true, "50+");
        var b = new ProductStockInfo(1L, "Racao", 79.90, true, "50+");
        var c = new ProductStockInfo(2L, "Racao", 79.90, true, "50+");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("Racao");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "50+, AVAILABLE",
            "20+, AVAILABLE",
            "LOW, LOW",
            "null, UNAVAILABLE"
    })
    void mapsStockRangeToAvailability(String stockRange, StockAvailability expected) {
        assertThat(new ProductStockInfo(1L, "Racao", 1.0, true, stockRange).availability()).isEqualTo(expected);
    }

    @Test
    void isEnabledTreatsNullAsDisabled() {
        assertThat(new ProductStockInfo(1L, "Racao", 1.0, null, "50+").isEnabled()).isFalse();
        assertThat(new ProductStockInfo(1L, "Racao", 1.0, false, "50+").isEnabled()).isFalse();
        assertThat(new ProductStockInfo(1L, "Racao", 1.0, true, "50+").isEnabled()).isTrue();
    }
}
