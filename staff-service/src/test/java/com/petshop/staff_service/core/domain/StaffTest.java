package com.petshop.staff_service.core.domain;

import com.petshop.commons.security.Role;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffTest {

    @Test
    void exposesAllFieldsViaAccessors() {
        var id = UUID.randomUUID();

        var staff = new Staff(id, "Ciclana", "12345678900", "ciclana@petshop.com", "11999990000", true, Role.GROOMER);

        assertThat(staff.getId()).isEqualTo(id);
        assertThat(staff.getName()).isEqualTo("Ciclana");
        assertThat(staff.getCpf()).isEqualTo("12345678900");
        assertThat(staff.getEmail()).isEqualTo("ciclana@petshop.com");
        assertThat(staff.getPhone()).isEqualTo("11999990000");
        assertThat(staff.getEnabled()).isTrue();
        assertThat(staff.getRole()).isEqualTo(Role.GROOMER);
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        var staff = new Staff();
        var id = UUID.randomUUID();

        staff.setId(id);
        staff.setName("Beltrano");
        staff.setCpf("11122233344");
        staff.setEmail("beltrano@petshop.com");
        staff.setPhone("11988887777");
        staff.setEnabled(false);
        staff.setRole(Role.VETERINARIAN);

        assertThat(staff.getId()).isEqualTo(id);
        assertThat(staff.getName()).isEqualTo("Beltrano");
        assertThat(staff.getCpf()).isEqualTo("11122233344");
        assertThat(staff.getEmail()).isEqualTo("beltrano@petshop.com");
        assertThat(staff.getPhone()).isEqualTo("11988887777");
        assertThat(staff.getEnabled()).isFalse();
        assertThat(staff.getRole()).isEqualTo(Role.VETERINARIAN);
    }

    @Test
    void equalsAndHashCodeConsiderOnlyId() {
        var id = UUID.randomUUID();
        var a = new Staff(id, "Ciclana", "12345678900", "ciclana@petshop.com", "11999990000", true, Role.GROOMER);
        var b = new Staff(id, "Nome Diferente", "00000000000", "outro@petshop.com", "11900000000", false, Role.ADMIN);
        var c = new Staff(UUID.randomUUID(), "Ciclana", "12345678900", "ciclana@petshop.com", "11999990000", true, Role.GROOMER);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }
}
