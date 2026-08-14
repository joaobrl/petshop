package com.petshop.customermanagement.api.rest.dto;

import com.petshop.customermanagement.core.domain.Customer;

import java.util.List;
import java.util.UUID;

public record CustomerResponseDto(
    UUID id,
    String name,
    String cpf,
    String email,
    String phone,
    List<PetResponseDto> pets,
    Boolean enabled

) {
    public CustomerResponseDto(Customer cliente) {
        this(
            cliente.getId(),
            cliente.getName(),
            cliente.getCpf(),
            cliente.getEmail(),
            cliente.getPhone(),
            cliente.getPet().stream().map(PetResponseDto::new).toList(),
            cliente.getEnabled()
        );
    }
}
