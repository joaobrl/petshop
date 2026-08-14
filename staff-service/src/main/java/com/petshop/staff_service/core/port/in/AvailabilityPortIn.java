package com.petshop.staff_service.core.port.in;

import com.petshop.staff_service.core.domain.AvailabilityResult;
import com.petshop.staff_service.core.domain.Staff;
import com.petshop.commons.security.Role;

import java.time.LocalDateTime;
import java.util.List;

public interface AvailabilityPortIn {

    AvailabilityResult checkGeneralAvailability(LocalDateTime dateTime, Role role);

    List<Staff> scheduledStaffFor(LocalDateTime dateTime, Role role);
}
