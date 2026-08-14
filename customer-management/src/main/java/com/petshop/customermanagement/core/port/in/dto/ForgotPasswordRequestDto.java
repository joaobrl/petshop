package com.petshop.customermanagement.core.port.in.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * cpf funciona como segundo fator (além do email) pra pedir uma senha
 * nova — sem ele, qualquer um que soubesse só o email conseguiria
 * disparar reset de senha de qualquer cliente.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ForgotPasswordRequestDto {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "CPF is required")
    @Pattern(regexp = "\\d{11}", message = "CPF must have 11 digits")
    private String cpf;
}
