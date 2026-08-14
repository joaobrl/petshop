package com.petshop.staff_service.core.port.out;

import com.petshop.staff_service.core.domain.Staff;

import java.util.List;

public interface StaffPortOut {
    List<Staff> findAll();
}
