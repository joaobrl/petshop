package com.petshop.staff_service.core.port.in;

import com.petshop.staff_service.core.domain.WeekendAllocation;

import java.util.List;

public interface WeekendAllocationPortIn {

    List<WeekendAllocation> generateNextMonth();
}
