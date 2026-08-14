package com.petshop.staff_service.core.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityResultTest {

    @Test
    void exposesAllFieldsViaAccessors() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);

        var result = new AvailabilityResult(3, true, dateTime);

        assertThat(result.getAvailableSlots()).isEqualTo(3);
        assertThat(result.isAvailable()).isTrue();
        assertThat(result.getValidatedDateTime()).isEqualTo(dateTime);
    }

    @Test
    void representsUnavailableSlot() {
        var dateTime = LocalDateTime.of(2026, 8, 16, 9, 0);

        var result = new AvailabilityResult(0, false, dateTime);

        assertThat(result.getAvailableSlots()).isZero();
        assertThat(result.isAvailable()).isFalse();
    }
}
