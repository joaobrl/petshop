package com.petshop.booking_service.core.port.out;

import com.petshop.booking_service.core.domain.StaffAssignment;

import java.time.LocalDateTime;
import java.util.List;

public interface CustomerManagementPortOut {

    List<StaffAssignment> getScheduledStaff(LocalDateTime dateTime, String role, String token);
}
