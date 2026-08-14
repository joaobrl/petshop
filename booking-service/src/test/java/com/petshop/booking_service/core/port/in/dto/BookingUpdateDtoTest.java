package com.petshop.booking_service.core.port.in.dto;

import com.petshop.booking_service.core.domain.enums.ServiceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BookingUpdateDtoTest {

    @Test
    void settersAndGettersRoundTrip() {
        var dto = new BookingUpdateDto();
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);

        dto.setServiceType(ServiceType.TOSAGEM);
        dto.setBookingDateTime(dateTime);
        dto.setObservations("Atualizado");

        assertThat(dto.getServiceType()).isEqualTo(ServiceType.TOSAGEM);
        assertThat(dto.getBookingDateTime()).isEqualTo(dateTime);
        assertThat(dto.getObservations()).isEqualTo("Atualizado");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var a = new BookingUpdateDto();
        a.setObservations("x");
        var b = new BookingUpdateDto();
        b.setObservations("x");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
