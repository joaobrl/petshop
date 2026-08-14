package com.petshop.staff_service.api.rest;

import com.petshop.staff_service.api.rest.dto.WeekendAllocationResponseDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@RequestMapping("/api/v1/weekend-allocation")
public interface WeekendAllocationController {

    @PostMapping("/generate")
    ResponseEntity<List<WeekendAllocationResponseDto>> generateNextMonth();

    @GetMapping
    ResponseEntity<List<WeekendAllocationResponseDto>> getAllocationsForDate(
            @RequestParam("date") @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate date);
}
