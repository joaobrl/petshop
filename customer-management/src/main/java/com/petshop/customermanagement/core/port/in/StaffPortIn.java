package com.petshop.customermanagement.core.port.in;

import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.in.dto.StaffRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StaffPortIn {

    Staff createStaff(StaffRequestDto request);

    Page<Staff> getAllStaff(Pageable pageable);

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
