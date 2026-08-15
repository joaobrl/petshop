package com.petshop.customermanagement.core.port.in.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerRequestDto {

    @NotBlank(message = "Nome é obrigatório")
    private String name;

    @NotBlank(message = "CPF é obrigatório")
    @Pattern(regexp = "\\d{11}", message = "CPF deve ter 11 dígitos")
    private String cpf;

    @NotBlank(message = "Email é obrigatório")
    @Email(message = "Formato de email inválido")
    private String email;

    @NotBlank(message = "Telefone é obrigatório")
    private String phone;

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 6, message = "Senha deve ter pelo menos 6 caracteres")
    private String password;

    @NotBlank(message = "Confirmação de senha é obrigatória")
    private String confirmPassword;

    // Validado aqui (não só no front) porque o cadastro é público e pode
    // ser chamado direto na API, sem passar por nenhum formulário.
    @AssertTrue(message = "Senha e confirmação de senha devem ser iguais")
    public boolean isConfirmPasswordMatching() {
        return password == null || password.equals(confirmPassword);
    }

}
