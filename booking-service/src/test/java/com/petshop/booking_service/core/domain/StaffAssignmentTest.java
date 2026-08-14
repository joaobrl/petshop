package com.petshop.booking_service.core.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StaffAssignmentTest {

    @Test
    void exposesFieldsViaAccessors() {
        var id = UUID.randomUUID();

        var assignment = new StaffAssignment(id, "Ciclana");

        assertThat(assignment.id()).isEqualTo(id);
        assertThat(assignment.name()).isEqualTo("Ciclana");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var id = UUID.randomUUID();

        var a = new StaffAssignment(id, "Ciclana");
        var b = new StaffAssignment(id, "Ciclana");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
