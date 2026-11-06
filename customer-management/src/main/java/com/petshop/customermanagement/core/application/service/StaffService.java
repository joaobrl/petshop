package com.petshop.customermanagement.core.application.service;

import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.in.StaffPortIn;
import com.petshop.customermanagement.core.port.in.dto.StaffRequestDto;
import com.petshop.customermanagement.core.port.out.IdentityProviderPortOut;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import com.petshop.customermanagement.core.port.out.dto.IdentityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class StaffService implements StaffPortIn {

    private final StaffPortOut staffPortOut;
    private final IdentityProviderPortOut identityProviderPortOut;

    // Mesmo esquema de consistência do CustomerService.registerCustomer: Keycloak antes do
    // commit local, com compensação se o commit falhar.
    @Override
    @Transactional
    public Staff createStaff(StaffRequestDto request) {
        staffPortOut.findByCpf(request.getCpf()).ifPresent(employee -> {
            throw new ConflictException("CPF já cadastrado");
        });
        staffPortOut.findByEmail(request.getEmail()).ifPresent(employee -> {
            throw new ConflictException("Email já cadastrado");
        });

        var saved = staffPortOut.save(new Staff(request));
        // Sem senha provisória (SDD-024 F3): o funcionário nasce sem credencial e define a
        // própria senha pelo e-mail de ação do Keycloak (UPDATE_PASSWORD).
        var idpUserId = identityProviderPortOut.createUser(IdentityUser.of(saved), null);
        IdentityCompensation.deleteOnRollback(identityProviderPortOut, idpUserId);
        sendSetPasswordEmail(idpUserId, saved);
        log.info("Staff registered: id={}", saved.getId());
        return saved;
    }

    // Falha de SMTP não desfaz o cadastro: o funcionário pode usar "Esqueci a senha" na
    // tela de login do Keycloak, ou o admin reenviar a ação pelo console.
    private void sendSetPasswordEmail(String idpUserId, Staff staff) {
        try {
            identityProviderPortOut.sendSetPasswordEmail(idpUserId);
        } catch (RuntimeException e) {
            log.warn("Set-password email not sent for staff: id={} ({})", staff.getId(), e.getClass().getSimpleName());
        }
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
                .orElseThrow(() -> new NotFoundException("Funcionário não encontrado(a)"));
    }

    private Optional<Staff> findByUuidIfValid(String idOrCpf) {
        try {
            return staffPortOut.findById(UUID.fromString(idOrCpf));
        } catch (IllegalArgumentException notAUuid) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Staff updateStaff(UUID id, StaffRequestDto request) {
        var staff = getStaffById(id);
        staff.update(request);
        var saved = staffPortOut.save(staff);
        // Papel, nome, e-mail e telefone saem do token: mantém o Keycloak igual.
        identityProviderPortOut.updateUser(IdentityUser.of(saved));
        log.info("Staff with ID: {} updated.", id);
        return saved;
    }

    @Override
    @Transactional
    public Staff deleteStaff(UUID id) {
        var staff = getStaffById(id);
        staff.setEnabled(false);
        var saved = staffPortOut.save(staff);
        // Funcionário desligado: usuário desabilitado no Keycloak (CA4).
        identityProviderPortOut.setEnabled(id, false);
        log.info("Staff with ID {} deleted.", id);
        return saved;
    }

    @Override
    @Transactional
    public Staff reactivateStaff(UUID id) {
        var staff = getStaffById(id);
        staff.setEnabled(true);
        var saved = staffPortOut.save(staff);
        identityProviderPortOut.setEnabled(id, true);
        log.info("Staff with ID {} reactivated.", id);
        return saved;
    }
}
