package com.petshop.booking_service.infrastructure.rest.customerManagement.feign.dto;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffDtoTest {

    @Test
    void exposesFieldsViaAccessors() {
        var id = UUID.randomUUID();

        var dto = new StaffDto(id, "Ciclana");

        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.name()).isEqualTo("Ciclana");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var id = UUID.randomUUID();

        var a = new StaffDto(id, "Ciclana");
        var b = new StaffDto(id, "Ciclana");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
