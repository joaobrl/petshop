package com.petshop.booking_service.core.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AvailableSlotTest {

    @Test
    void exposesFieldsViaAccessors() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);

        var slot = new AvailableSlot(dateTime, 3);

        assertThat(slot.dateTime()).isEqualTo(dateTime);
        assertThat(slot.availableStaff()).isEqualTo(3);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);

        var a = new AvailableSlot(dateTime, 3);
        var b = new AvailableSlot(dateTime, 3);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("availableStaff");
    }
}
