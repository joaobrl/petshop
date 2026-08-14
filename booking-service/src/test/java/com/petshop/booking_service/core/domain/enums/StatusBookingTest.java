package com.petshop.booking_service.core.domain.enums;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusBookingTest {

    @Test
    void hasExactlyThreeValues() {
        assertThat(StatusBooking.values()).containsExactly(
                StatusBooking.SCHEDULED, StatusBooking.COMPLETED, StatusBooking.CANCELED);
    }

    @Test
    void valueOfResolvesByName() {
        assertThat(StatusBooking.valueOf("CANCELED")).isEqualTo(StatusBooking.CANCELED);
    }
}
