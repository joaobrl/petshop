package com.petshop.staff_service.infrastructure.rest.customermanagement.dto;

import com.petshop.commons.security.Role;
import lombok.Data;

import java.util.UUID;

@Data
public class StaffRegistryResponseDto {
    private UUID id;
    private String name;
    private String cpf;
    private String email;
    private String phone;
    private Boolean enabled;
    private Role role;
}
