package com.petshop.staff_service.core.port.out.dto;

import com.petshop.staff_service.core.domain.Staff;
import com.petshop.commons.security.Role;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffResponseDtoTest {

    @Test
    void mapsFromStaff() {
        var id = UUID.randomUUID();
        var staff = new Staff(id, "Ciclana", "12345678900", "ciclana@petshop.com", "11999990000", true, Role.GROOMER);

        var dto = new StaffResponseDto(staff);

        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getName()).isEqualTo("Ciclana");
        assertThat(dto.getCpf()).isEqualTo("12345678900");
        assertThat(dto.getEmail()).isEqualTo("ciclana@petshop.com");
        assertThat(dto.getPhone()).isEqualTo("11999990000");
        assertThat(dto.getEnabled()).isTrue();
        assertThat(dto.getRole()).isEqualTo(Role.GROOMER);
    }

    @Test
    void settersRoundTrip() {
        var dto = new StaffResponseDto(new Staff(UUID.randomUUID(), "X", "1", "x@x.com", "1", true, Role.ADMIN));
        var newId = UUID.randomUUID();

        dto.setId(newId);
        dto.setName("Beltrano");
        dto.setCpf("11122233344");
        dto.setEmail("beltrano@petshop.com");
        dto.setPhone("11988887777");
        dto.setEnabled(false);
        dto.setRole(Role.RECEPTIONIST);

        assertThat(dto.getId()).isEqualTo(newId);
        assertThat(dto.getName()).isEqualTo("Beltrano");
        assertThat(dto.getCpf()).isEqualTo("11122233344");
        assertThat(dto.getEmail()).isEqualTo("beltrano@petshop.com");
        assertThat(dto.getPhone()).isEqualTo("11988887777");
        assertThat(dto.getEnabled()).isFalse();
        assertThat(dto.getRole()).isEqualTo(Role.RECEPTIONIST);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var id = UUID.randomUUID();
        var staff = new Staff(id, "Ciclana", "12345678900", "ciclana@petshop.com", "11999990000", true, Role.GROOMER);

        var a = new StaffResponseDto(staff);
        var b = new StaffResponseDto(staff);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("name");
    }
}
