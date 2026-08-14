package com.petshop.booking_service.core.port.out.dto;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingResponseDtoTest {

    @Test
    void mapsFromBooking() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var request = new BookingRequestDto();
        request.setPetId(UUID.randomUUID().toString());
        request.setServiceType(ServiceType.BANHO);
        request.setBookingDateTime(dateTime);
        request.setObservations("Observação");

        var booking = new Booking(request);
        booking.setId(UUID.randomUUID());
        booking.setPetId(UUID.randomUUID());
        booking.setOwnerName("Ciclana");
        booking.setOwnerCpf("12345678900");
        booking.setOwnerContact("11999990000");
        booking.setOwnerEmail("ciclana@petshop.com");

        var dto = new BookingResponseDto(booking);

        assertThat(dto.getId()).isEqualTo(booking.getId());
        assertThat(dto.getPetId()).isEqualTo(booking.getPetId());
        assertThat(dto.getOwnerName()).isEqualTo("Ciclana");
        assertThat(dto.getOwnerCpf()).isEqualTo("12345678900");
        assertThat(dto.getOwnerContact()).isEqualTo("11999990000");
        assertThat(dto.getOwnerEmail()).isEqualTo("ciclana@petshop.com");
        assertThat(dto.getServiceType().getServiceType()).isEqualTo(ServiceType.BANHO);
        assertThat(dto.getServiceType().getPrice()).isEqualByComparingTo("80.00");
        assertThat(dto.getServiceType().getDurationInMinutes()).isEqualTo(45);
        assertThat(dto.getStatus()).isEqualTo(booking.getStatus());
        assertThat(dto.getBookingDateTime()).isEqualTo(dateTime);
        assertThat(dto.getObservations()).isEqualTo("Observação");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var request = new BookingRequestDto();
        request.setPetId(UUID.randomUUID().toString());
        request.setServiceType(ServiceType.BANHO);
        request.setBookingDateTime(LocalDateTime.of(2026, 8, 15, 9, 0));
        var booking = new Booking(request);
        booking.setId(UUID.randomUUID());

        var a = new BookingResponseDto(booking);
        var b = new BookingResponseDto(booking);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
