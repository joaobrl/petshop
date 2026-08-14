package com.petshop.customermanagement.core.port.in;

import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.in.dto.StaffRequestDto;

import java.util.List;
import java.util.UUID;

public interface StaffPortIn {

    Staff createStaff(StaffRequestDto request);

    List<Staff> getAllStaff();

    Staff getStaffById(UUID id);

    /**
     * Busca flexível pro endpoint {@code GET /{id}/find}: tenta o valor
     * como UUID primeiro, se não achar (ou não for um UUID válido) tenta
     * como CPF.
     */
    Staff getStaffByIdOrCpf(String idOrCpf);

    Staff updateStaff(UUID id, StaffRequestDto request);

    Staff deleteStaff(UUID id);

    Staff reactivateStaff(UUID id);
}
