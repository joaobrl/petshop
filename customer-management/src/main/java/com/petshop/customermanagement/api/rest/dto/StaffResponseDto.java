package com.petshop.customermanagement.api.rest.dto;

import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.domain.Staff;
import lombok.Data;

import java.util.UUID;

@Data
public class StaffResponseDto {

    private UUID id;
    private String name;
    private String cpf;
    private String email;
    private String phone;
    private Boolean enabled;
    private Role role;

    public StaffResponseDto(Staff staff) {
        this.id = staff.getId();
        this.name = staff.getName();
        this.cpf = staff.getCpf();
        this.email = staff.getEmail();
        this.phone = staff.getPhone();
        this.enabled = staff.getEnabled();
        this.role = staff.getRole();
    }
}
