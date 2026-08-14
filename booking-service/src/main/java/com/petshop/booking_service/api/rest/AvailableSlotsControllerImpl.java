package com.petshop.booking_service.api.rest;

import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.port.in.AvailableSlotsPortIn;
import com.petshop.booking_service.core.port.out.dto.AvailableSlotResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AvailableSlotsControllerImpl implements AvailableSlotsController {

    private final AvailableSlotsPortIn portIn;

    @Override
    public ResponseEntity<List<AvailableSlotResponseDto>> getAvailableSlots(
            ServiceType serviceType, LocalDate date, RangeType range) {
        var slots = portIn.findAvailableSlots(serviceType, date, range).stream()
                .map(AvailableSlotResponseDto::new)
                .toList();
        return ResponseEntity.ok(slots);
    }
}
