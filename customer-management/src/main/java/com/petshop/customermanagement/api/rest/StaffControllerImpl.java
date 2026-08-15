package com.petshop.customermanagement.api.rest;

import com.petshop.commons.dto.PageResponse;
import com.petshop.customermanagement.api.rest.dto.StaffResponseDto;
import com.petshop.customermanagement.core.port.in.StaffPortIn;
import com.petshop.customermanagement.core.port.in.dto.StaffRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class StaffControllerImpl implements StaffController {

    private final StaffPortIn portIn;

    @Override
    public ResponseEntity<StaffResponseDto> createStaff(@Valid StaffRequestDto request, UriComponentsBuilder uriBuilder) {
        var staff = portIn.createStaff(request);
        var uri = uriBuilder.path("/api/v1/staff/{id}/find").buildAndExpand(staff.getId()).toUri();
        return ResponseEntity.created(uri).body(new StaffResponseDto(staff));
    }

    @Override
    public ResponseEntity<PageResponse<StaffResponseDto>> getAllStaff(Pageable pageable) {
        var page = portIn.getAllStaff(pageable).map(StaffResponseDto::new);
        return ResponseEntity.ok(new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast()));
    }

    @Override
    public ResponseEntity<StaffResponseDto> getStaffById(String id) {
        return ResponseEntity.ok(new StaffResponseDto(portIn.getStaffByIdOrCpf(id)));
    }

    @Override
    public ResponseEntity<StaffResponseDto> updateStaff(UUID id, StaffRequestDto request) {
        return ResponseEntity.ok(new StaffResponseDto(portIn.updateStaff(id, request)));
    }

    @Override
    public ResponseEntity<Void> deleteStaff(UUID id) {
        portIn.deleteStaff(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<StaffResponseDto> reactivateStaff(UUID id) {
        return ResponseEntity.ok(new StaffResponseDto(portIn.reactivateStaff(id)));
    }
}
