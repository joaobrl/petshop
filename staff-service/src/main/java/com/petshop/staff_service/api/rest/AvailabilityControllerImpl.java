package com.petshop.staff_service.api.rest;

import com.petshop.staff_service.api.rest.dto.AvailabilityResponseDto;
import com.petshop.commons.security.Role;
import com.petshop.staff_service.core.port.in.AvailabilityPortIn;
import com.petshop.staff_service.core.port.out.dto.StaffResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AvailabilityControllerImpl implements AvailabilityController {

    private final AvailabilityPortIn portIn;

    @Override
    public ResponseEntity<AvailabilityResponseDto> getGeneralAvailability(LocalDateTime dateTime, Role role) {
        var result = portIn.checkGeneralAvailability(dateTime, role);
        return ResponseEntity.ok(new AvailabilityResponseDto(result));
    }

    @Override
    public ResponseEntity<List<StaffResponseDto>> getSchedule(LocalDateTime dateTime, Role role) {
        var staffOnDuty = portIn.scheduledStaffFor(dateTime, role)
                .stream()
                .map(StaffResponseDto::new)
                .toList();
        return ResponseEntity.ok(staffOnDuty);
    }
}
