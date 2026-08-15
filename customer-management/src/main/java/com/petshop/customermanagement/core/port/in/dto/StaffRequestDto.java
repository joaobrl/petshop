package com.petshop.customermanagement.core.port.in.dto;

import com.petshop.commons.security.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class StaffRequestDto {

    @NotBlank(message = "Nome é obrigatório")
    private String name;

    @NotBlank(message = "CPF é obrigatório")
    @Pattern(regexp = "\\d{11}", message = "CPF deve ter 11 dígitos")
    private String cpf;

    @NotBlank(message = "Email é obrigatório")
    private String email;

    @NotBlank(message = "Telefone é obrigatório")
    private String phone;

    @NotNull(message = "Cargo é obrigatório")
    private Role role;
}
