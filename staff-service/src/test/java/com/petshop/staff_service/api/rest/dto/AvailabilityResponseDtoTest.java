package com.petshop.staff_service.api.rest.dto;

import com.petshop.staff_service.core.domain.AvailabilityResult;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityResponseDtoTest {

    @Test
    void mapsFromAvailabilityResultAndFormatsDateTime() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 30);
        var result = new AvailabilityResult(2, true, dateTime);

        var dto = new AvailabilityResponseDto(result);

        assertThat(dto.getAvailableSlots()).isEqualTo(2);
        assertThat(dto.getIsAvailable()).isTrue();
        assertThat(dto.getValidatedDateTime()).isEqualTo("15/08/2026 09:30");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 30);
        var a = new AvailabilityResponseDto(new AvailabilityResult(2, true, dateTime));
        var b = new AvailabilityResponseDto(new AvailabilityResult(2, true, dateTime));

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("availableSlots");
    }
}
