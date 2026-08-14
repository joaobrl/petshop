package com.petshop.order_service.infrastructure.rest.customer.feign.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerInfoResponseDtoTest {

    @Test
    void exposesAllFieldsViaAccessors() {
        var id = UUID.randomUUID();

        var dto = new CustomerInfoResponseDto(id, "Maria", "maria@mail.com");

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.name()).isEqualTo("Maria");
        assertThat(dto.email()).isEqualTo("maria@mail.com");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var id = UUID.randomUUID();
        var a = new CustomerInfoResponseDto(id, "Maria", "maria@mail.com");
        var b = new CustomerInfoResponseDto(id, "Maria", "maria@mail.com");
        var c = new CustomerInfoResponseDto(UUID.randomUUID(), "Maria", "maria@mail.com");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("Maria");
    }
}
