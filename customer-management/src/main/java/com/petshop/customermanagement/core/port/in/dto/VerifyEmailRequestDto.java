package com.petshop.customermanagement.core.port.in.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VerifyEmailRequestDto {

    @NotBlank(message = "Token é obrigatório")
    private String token;
}
