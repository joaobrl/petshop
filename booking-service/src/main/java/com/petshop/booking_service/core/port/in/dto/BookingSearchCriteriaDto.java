package com.petshop.booking_service.core.port.in.dto;

import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class BookingSearchCriteriaDto {
    private String ownerCpf;
    private UUID petId;
    private LocalDate date;
    private ServiceType serviceType;
    private String employeeName;
    private StatusBooking status;

    public BookingSearchCriteriaDto(String ownerCpf, UUID petId, String serviceType, String status, LocalDate date, String employeeName) {
        this.ownerCpf = ownerCpf;
        this.petId = petId;
        this.date = date;
        this.employeeName = employeeName;

        if (serviceType != null && !serviceType.isBlank()) {
            try {
                this.serviceType = ServiceType.valueOf(serviceType.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Tipo de serviço inválido: " + serviceType);
            }
        }

        if (status != null && !status.isBlank()) {
            try {
                this.status = StatusBooking.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Status inválido: " + status);
            }
        }
    }
}
