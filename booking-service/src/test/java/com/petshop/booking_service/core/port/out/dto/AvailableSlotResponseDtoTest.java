package com.petshop.booking_service.core.port.out.dto;

import com.petshop.booking_service.core.domain.AvailableSlot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AvailableSlotResponseDtoTest {

    @Test
    void mapsFromAvailableSlotAndFormatsDateTime() {
        var slot = new AvailableSlot(LocalDateTime.of(2026, 8, 15, 9, 30), 2);

        var dto = new AvailableSlotResponseDto(slot);

        assertThat(dto.getDateTime()).isEqualTo("15/08/2026 09:30");
        assertThat(dto.getAvailableStaff()).isEqualTo(2);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var slot = new AvailableSlot(LocalDateTime.of(2026, 8, 15, 9, 30), 2);

        var a = new AvailableSlotResponseDto(slot);
        var b = new AvailableSlotResponseDto(slot);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("availableStaff");
    }
}
