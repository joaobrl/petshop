package com.petshop.staff_service.api.rest;

import com.petshop.staff_service.api.rest.dto.AvailabilityResponseDto;
import com.petshop.commons.security.Role;
import com.petshop.staff_service.core.port.out.dto.StaffResponseDto;
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
            @RequestParam("dateTime") @DateTimeFormat(pattern = "dd/MM/yyyy HH:mm") LocalDateTime dateTime,
            @RequestParam("role") Role role);


    @GetMapping("/schedule")
    ResponseEntity<List<StaffResponseDto>> getSchedule(
            @RequestParam("dateTime") @DateTimeFormat(pattern = "dd/MM/yyyy HH:mm") LocalDateTime dateTime,
            @RequestParam("role") Role role);
}
