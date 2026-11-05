package com.petshop.staff_service.api.rest;

import com.petshop.staff_service.api.rest.dto.AvailabilityResponseDto;
import com.petshop.commons.security.Role;
import com.petshop.staff_service.core.port.out.dto.StaffResponseDto;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.List;

@RequestMapping("/api/v1/availability")
public interface AvailabilityController {

    @GetMapping("/general")
    ResponseEntity<AvailabilityResponseDto> getGeneralAvailability(
            @Parameter(description = "Data e hora em ISO-8601 sem offset (horário de Brasília)", example = "2026-08-15T09:00:00")
            @RequestParam("dateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime,
            @RequestParam("role") Role role);


    @GetMapping("/schedule")
    ResponseEntity<List<StaffResponseDto>> getSchedule(
            @Parameter(description = "Data e hora em ISO-8601 sem offset (horário de Brasília)", example = "2026-08-15T09:00:00")
            @RequestParam("dateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTime,
            @RequestParam("role") Role role);
}
