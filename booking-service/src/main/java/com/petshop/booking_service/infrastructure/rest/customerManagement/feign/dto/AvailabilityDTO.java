package com.petshop.booking_service.infrastructure.rest.customerManagement.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AvailabilityDTO {

    private Integer availableSlots;

    private Boolean isAvailable;

    private String validatedDateTime;
}
