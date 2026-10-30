package com.petshop.order_service.core.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Item do carrinho. productName e unitPrice são um "retrato" do produto no
 * momento em que foi adicionado — se o preço mudar depois no catálogo, o
 * carrinho não muda de valor por baixo do cliente. O carrinho não reserva
 * estoque: a reserva só acontece no checkout (ver OrderService).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

    private Long productId;
    private String productName;
    private Integer quantity;
    private Double unitPrice;

    public double subtotal() {
        return unitPrice * quantity;
    }
}
