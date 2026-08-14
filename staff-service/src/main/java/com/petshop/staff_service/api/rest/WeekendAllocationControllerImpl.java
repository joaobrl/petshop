package com.petshop.staff_service.api.rest;

import com.petshop.staff_service.api.rest.dto.WeekendAllocationResponseDto;
import com.petshop.staff_service.core.port.in.WeekendAllocationPortIn;
import com.petshop.staff_service.core.port.out.WeekendAllocationPortOut;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class WeekendAllocationControllerImpl implements WeekendAllocationController {

    private final WeekendAllocationPortIn portIn;
    private final WeekendAllocationPortOut portOut;

    @Override
    public ResponseEntity<List<WeekendAllocationResponseDto>> generateNextMonth() {
        var allocations = portIn.generateNextMonth().stream()
                .map(WeekendAllocationResponseDto::new)
                .toList();
        return ResponseEntity.ok(allocations);
    }

    @Override
    public ResponseEntity<List<WeekendAllocationResponseDto>> getAllocationsForDate(LocalDate date) {
        var allocations = portOut.findByDate(date).stream()
                .map(WeekendAllocationResponseDto::new)
                .toList();
        return ResponseEntity.ok(allocations);
    }
}
