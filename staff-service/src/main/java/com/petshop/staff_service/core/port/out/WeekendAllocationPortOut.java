package com.petshop.staff_service.core.port.out;

import com.petshop.staff_service.core.domain.WeekendAllocation;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeekendAllocationPortOut {

    Optional<LocalDate> findLastAllocatedDate();

    List<WeekendAllocation> findByDate(LocalDate date);

    long countAll();

    List<WeekendAllocation> saveAll(List<WeekendAllocation> allocations);
}
