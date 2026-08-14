package com.petshop.booking_service.infrastructure.rest.customerManagement.feign.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityDTOTest {

    @Test
    void allArgsConstructorAndGetters() {
        var dto = new AvailabilityDTO(2, true, "15/08/2026 09:00");

        assertThat(dto.getAvailableSlots()).isEqualTo(2);
        assertThat(dto.getIsAvailable()).isTrue();
        assertThat(dto.getValidatedDateTime()).isEqualTo("15/08/2026 09:00");
    }

    @Test
    void noArgsConstructorAndSettersRoundTrip() {
        var dto = new AvailabilityDTO();
        dto.setAvailableSlots(1);
        dto.setIsAvailable(false);
        dto.setValidatedDateTime("16/08/2026 10:00");

        assertThat(dto.getAvailableSlots()).isEqualTo(1);
        assertThat(dto.getIsAvailable()).isFalse();
        assertThat(dto.getValidatedDateTime()).isEqualTo("16/08/2026 10:00");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var a = new AvailabilityDTO(2, true, "15/08/2026 09:00");
        var b = new AvailabilityDTO(2, true, "15/08/2026 09:00");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("availableSlots");
    }
}
