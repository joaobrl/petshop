package com.petshop.customermanagement.core.port.in.dto;

import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CustomerUpdateDto {

    private String name;
    @Email(message = "Invalid email format")
    private String email;
    private String phone;

}