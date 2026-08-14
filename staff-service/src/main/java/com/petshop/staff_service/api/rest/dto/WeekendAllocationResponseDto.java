package com.petshop.staff_service.api.rest.dto;

import com.petshop.staff_service.core.domain.WeekendAllocation;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
public class WeekendAllocationResponseDto {

    private LocalDate allocationDate;
    private UUID staffId;
    private String staffName;

    public WeekendAllocationResponseDto(WeekendAllocation allocation) {
        this.allocationDate = allocation.getAllocationDate();
        this.staffId = allocation.getStaffId();
        this.staffName = allocation.getStaffName();
    }
}
