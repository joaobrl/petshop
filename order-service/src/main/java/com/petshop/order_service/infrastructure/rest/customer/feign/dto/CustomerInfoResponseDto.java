package com.petshop.order_service.infrastructure.rest.customer.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerInfoResponseDto(UUID id, String name, String email) {
}
