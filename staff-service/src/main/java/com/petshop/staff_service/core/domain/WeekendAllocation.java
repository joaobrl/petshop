package com.petshop.staff_service.core.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class WeekendAllocation {

    private UUID id;

    private LocalDate allocationDate;

    private UUID staffId;

    private String staffName;

    public WeekendAllocation(LocalDate allocationDate, Staff staff) {
        this.allocationDate = allocationDate;
        this.staffId = staff.getId();
        this.staffName = staff.getName();
    }
}
