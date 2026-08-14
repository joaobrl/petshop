package com.petshop.order_service.core.port.in.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class AddCartItemRequestDto {

    @NotNull(message = "customerId é obrigatório")
    private UUID customerId;

    @NotNull(message = "productId é obrigatório")
    private Long productId;

    @NotNull(message = "quantity é obrigatório")
    @Positive(message = "quantity deve ser maior que zero")
    private Integer quantity;
}
