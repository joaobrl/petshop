package com.petshop.product_registration.core.port.in.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ProductRequestDto {
    @NotBlank
    private String name;
    private String description;
    @NotNull(message = "O preço é obrigatório")
    @Positive(message = "O preço deve ser maior que zero")
    private Double price;
    private Integer stock;
    @Valid
    private DimensionsDto dimensions;

}
