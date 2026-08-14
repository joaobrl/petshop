package com.petshop.booking_service.core.port.out.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
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

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
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
