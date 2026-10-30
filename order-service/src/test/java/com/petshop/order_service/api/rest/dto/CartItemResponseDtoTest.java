package com.petshop.order_service.api.rest.dto;

import com.petshop.order_service.core.domain.CartItem;
import com.petshop.order_service.core.domain.enums.StockAvailability;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartItemResponseDtoTest {

    @Test
    void copiesAllFieldsAndComputesSubtotal() {
        var item = new CartItem(1L, "Racao", 3, 10.0);

        var dto = new CartItemResponseDto(item, StockAvailability.LOW);

        assertThat(dto.getProductId()).isEqualTo(1L);
        assertThat(dto.getProductName()).isEqualTo("Racao");
        assertThat(dto.getQuantity()).isEqualTo(3);
        assertThat(dto.getUnitPrice()).isEqualTo(10.0);
        assertThat(dto.getSubtotal()).isEqualTo(30.0);
        assertThat(dto.getAvailability()).isEqualTo(StockAvailability.LOW);
    }

    @Test
    void settersUpdateFieldsDirectly() {
        var dto = new CartItemResponseDto(new CartItem(1L, "Racao", 1, 10.0), null);

        dto.setProductId(2L);
        dto.setProductName("Brinquedo");
        dto.setQuantity(5);
        dto.setUnitPrice(2.0);
        dto.setSubtotal(10.0);

        assertThat(dto.getProductId()).isEqualTo(2L);
        assertThat(dto.getProductName()).isEqualTo("Brinquedo");
        assertThat(dto.getQuantity()).isEqualTo(5);
        assertThat(dto.getUnitPrice()).isEqualTo(2.0);
        assertThat(dto.getSubtotal()).isEqualTo(10.0);
    }
}
