package com.petshop.customermanagement.core.application.service;

import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.in.StaffPortIn;
import com.petshop.customermanagement.core.port.in.dto.StaffRequestDto;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffService implements StaffPortIn {

    private final StaffPortOut staffPortOut;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Staff createStaff(StaffRequestDto request) {
        staffPortOut.findByCpf(request.getCpf()).ifPresent(employee -> {
            throw new ConflictException("CPF já cadastrado: " + request.getCpf());
        });
        staffPortOut.findByEmail(request.getEmail()).ifPresent(employee -> {
            throw new ConflictException("Email já cadastrado: " + request.getEmail());
        });

        var staff = new Staff(request);
        // Senha inicial = o próprio cpf, igual Customer — troca no primeiro
        // login (ver AuthService/mustChangePassword).
        staff.setPasswordHash(passwordEncoder.encode(request.getCpf()));
        log.info("Staff registered with CPF: {}", request.getCpf());
        return staffPortOut.save(staff);
    }

    @Override
    public Page<Staff> getAllStaff(Pageable pageable) {
        return staffPortOut.findAllByEnabledTrue(pageable);
    }

    @Override
    public Staff getStaffById(UUID id) {
        return staffPortOut.findById(id)
                .orElseThrow(() -> new NotFoundException("Funcionário", id));
    }

    @Override
    public Staff getStaffByIdOrCpf(String idOrCpf) {
        return findByUuidIfValid(idOrCpf)
                .or(() -> staffPortOut.findByCpf(idOrCpf))
                .orElseThrow(() -> new NotFoundException("Funcionário", idOrCpf));
    }

    private Optional<Staff> findByUuidIfValid(String idOrCpf) {
        try {
            return staffPortOut.findById(UUID.fromString(idOrCpf));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    @Override
    public Staff updateStaff(UUID id, StaffRequestDto request) {
        var staff = getStaffById(id);
        staff.update(request);
        log.info("Staff with ID: {} updated.", id);
        return staffPortOut.save(staff);
    }

    @Override
    public Staff deleteStaff(UUID id) {
        var staff = getStaffById(id);
        staff.setEnabled(false);
        log.info("Staff with ID {} deleted.", id);
        return staffPortOut.save(staff);
    }

    @Override
    public Staff reactivateStaff(UUID id) {
        var staff = getStaffById(id);
        staff.setEnabled(true);
        log.info("Staff with ID {} reactivated.", id);
        return staffPortOut.save(staff);
    }
}
