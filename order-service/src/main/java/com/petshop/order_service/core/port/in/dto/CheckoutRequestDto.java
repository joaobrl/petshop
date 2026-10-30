package com.petshop.order_service.core.port.in.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Itens do carrinho que o cliente marcou para comprar. Lista vazia ou item
 * que não está no carrinho viram 422 no OrderService (regra de negócio, não
 * de formato).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequestDto {

    private List<Long> productIds;
}
