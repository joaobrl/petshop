package com.petshop.booking_service.infrastructure.rest.customerManagement.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StaffDto(UUID id, String name) {
}
