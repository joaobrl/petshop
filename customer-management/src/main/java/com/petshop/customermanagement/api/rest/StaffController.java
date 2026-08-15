package com.petshop.customermanagement.api.rest;

import com.petshop.commons.dto.PageResponse;
import com.petshop.customermanagement.api.rest.dto.StaffResponseDto;
import com.petshop.customermanagement.core.port.in.dto.StaffRequestDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

/**
 * Só ADMIN gerencia funcionários — ver SecurityConfig. {@code GET /all}
 * também é chamado via Feign pelo staff-service, para montar disponibilidade/escala.
 */
@RequestMapping("/api/v1/staff")
public interface StaffController {

    @PostMapping("/create")
    ResponseEntity<StaffResponseDto> createStaff(@Valid @RequestBody StaffRequestDto request, UriComponentsBuilder uriBuilder);

    @GetMapping("/all")
    ResponseEntity<PageResponse<StaffResponseDto>> getAllStaff(@PageableDefault(size = 20) Pageable pageable);

    @GetMapping("/{id}/find")
    ResponseEntity<StaffResponseDto> getStaffById(@PathVariable String id);

    @PatchMapping("/{id}/update")
    ResponseEntity<StaffResponseDto> updateStaff(@PathVariable UUID id, @RequestBody StaffRequestDto request);

    @DeleteMapping("/{id}/delete")
    ResponseEntity<Void> deleteStaff(@PathVariable UUID id);

    @PatchMapping("/{id}/reactivate")
    ResponseEntity<StaffResponseDto> reactivateStaff(@PathVariable UUID id);
}
