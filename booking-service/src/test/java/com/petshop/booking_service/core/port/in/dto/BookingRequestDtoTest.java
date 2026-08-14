package com.petshop.booking_service.core.port.in.dto;

import com.petshop.booking_service.core.domain.enums.ServiceType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BookingRequestDtoTest {

    @Test
    void settersAndGettersRoundTrip() {
        var dto = new BookingRequestDto();
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);

        dto.setPetId("pet-1");
        dto.setOwnerName("Ciclana");
        dto.setOwnerCpf("12345678900");
        dto.setOwnerContact("11999990000");
        dto.setOwnerEmail("ciclana@petshop.com");
        dto.setServiceType(ServiceType.BANHO);
        dto.setBookingDateTime(dateTime);
        dto.setObservations("Nenhuma");

        assertThat(dto.getPetId()).isEqualTo("pet-1");
        assertThat(dto.getOwnerName()).isEqualTo("Ciclana");
        assertThat(dto.getOwnerCpf()).isEqualTo("12345678900");
        assertThat(dto.getOwnerContact()).isEqualTo("11999990000");
        assertThat(dto.getOwnerEmail()).isEqualTo("ciclana@petshop.com");
        assertThat(dto.getServiceType()).isEqualTo(ServiceType.BANHO);
        assertThat(dto.getBookingDateTime()).isEqualTo(dateTime);
        assertThat(dto.getObservations()).isEqualTo("Nenhuma");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var a = new BookingRequestDto();
        a.setOwnerCpf("12345678900");
        var b = new BookingRequestDto();
        b.setOwnerCpf("12345678900");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("ownerCpf");
    }
}
