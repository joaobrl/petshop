package com.petshop.booking_service.core.port.out.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class BookingResponseDto {

    private UUID id;

    private UUID petId;

    private String ownerName;

    private String ownerCpf;

    private String ownerContact;

    private String ownerEmail;

    private ServiceDetailsResponseDto serviceType;

    // Shape STRING sem pattern = ISO-8601 (2026-08-15T09:00:00). Explícito porque este
    // DTO também é o payload dos eventos Kafka de histórico, serializado pelo
    // JsonSerializer do spring-kafka (Jackson 2, WRITE_DATES_AS_TIMESTAMPS ligado),
    // que sem isso emitiria a data como array [2026,8,15,9,0].
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "Data e hora em ISO-8601 sem offset (horário de Brasília)", example = "2026-08-15T09:00:00")
    private LocalDateTime bookingDateTime;

    private StatusBooking status;

    private String observations;

    public BookingResponseDto(Booking booking) {
        this.id = booking.getId();
        this.petId = booking.getPetId();
        this.ownerName = booking.getOwnerName();
        this.ownerCpf = booking.getOwnerCpf();
        this.ownerContact = booking.getOwnerContact();
        this.ownerEmail = booking.getOwnerEmail();

        this.serviceType = new ServiceDetailsResponseDto();
        this.serviceType.setServiceType(booking.getServiceDetails().getServiceType());
        this.serviceType.setPrice(booking.getServiceDetails().getPrice());
        this.serviceType.setDurationInMinutes(booking.getServiceDetails().getDurationInMinutes());
        this.serviceType.setPaymentStatus(booking.getServiceDetails().getPaymentStatus());
        this.serviceType.setPaymentMethod(booking.getServiceDetails().getPaymentMethod());

        this.status = booking.getStatus();
        this.bookingDateTime = booking.getBookingDateTime();
        this.observations = booking.getObservations();
    }
}
