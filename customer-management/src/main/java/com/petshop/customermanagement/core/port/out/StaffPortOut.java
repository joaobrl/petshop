package com.petshop.customermanagement.core.port.out;

import com.petshop.customermanagement.core.domain.Staff;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StaffPortOut {

    Optional<Staff> findByCpf(String cpf);

    Optional<Staff> findByEmail(String email);

    Optional<Staff> findById(UUID id);

    List<Staff> findAll();

    Page<Staff> findAllByEnabledTrue(Pageable pageable);

    Staff save(Staff staff);
}
