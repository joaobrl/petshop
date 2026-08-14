package com.petshop.order_service.core.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Item do carrinho. productName e unitPrice são um "retrato" do produto no
 * momento em que foi adicionado — se o preço mudar depois no catálogo, o
 * carrinho não muda de valor por baixo do cliente.
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

    /**
     * Estoque reservado no product-registration; falso se adicionado sem
     * login (reserva só ocorre no checkout). É um flag por item, não por
     * quantidade: incrementos sem login não rebaixam um item já reservado.
     */
    private boolean reserved;

    public double subtotal() {
        return unitPrice * quantity;
    }
}
