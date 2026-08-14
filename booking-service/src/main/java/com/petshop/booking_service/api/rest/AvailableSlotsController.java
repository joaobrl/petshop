package com.petshop.booking_service.api.rest;

import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.port.out.dto.AvailableSlotResponseDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@RequestMapping("/api/v1/bookings")
public interface AvailableSlotsController {

    @GetMapping("/available-slots")
    ResponseEntity<List<AvailableSlotResponseDto>> getAvailableSlots(
            @RequestParam("serviceType") ServiceType serviceType,
            @RequestParam("date") @DateTimeFormat(pattern = "dd/MM/yyyy") LocalDate date,
            @RequestParam("range") RangeType range);
}
