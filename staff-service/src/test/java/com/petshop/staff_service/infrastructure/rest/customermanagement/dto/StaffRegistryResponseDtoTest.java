package com.petshop.staff_service.infrastructure.rest.customermanagement.dto;

import com.petshop.commons.security.Role;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffRegistryResponseDtoTest {

    @Test
    void settersAndGettersRoundTrip() {
        var dto = new StaffRegistryResponseDto();
        var id = UUID.randomUUID();

        dto.setId(id);
        dto.setName("Ciclana");
        dto.setCpf("12345678900");
        dto.setEmail("ciclana@petshop.com");
        dto.setPhone("11999990000");
        dto.setEnabled(true);
        dto.setRole(Role.GROOMER);

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getName()).isEqualTo("Ciclana");
        assertThat(dto.getCpf()).isEqualTo("12345678900");
        assertThat(dto.getEmail()).isEqualTo("ciclana@petshop.com");
        assertThat(dto.getPhone()).isEqualTo("11999990000");
        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getRole()).isEqualTo(Role.GROOMER);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var id = UUID.randomUUID();

        var a = new StaffRegistryResponseDto();
        a.setId(id);
        a.setName("Ciclana");

        var b = new StaffRegistryResponseDto();
        b.setId(id);
        b.setName("Ciclana");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("name");
    }
}
