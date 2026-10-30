package com.petshop.order_service.core.domain;

import com.petshop.order_service.core.domain.enums.StockAvailability;

import java.util.List;
import java.util.Map;

/**
 * Carrinho como o cliente vê: o {@link Cart} persistido mais a
 * disponibilidade atual de cada item (não persistida, consultada no
 * product-registration a cada leitura) e os nomes dos produtos que saíram
 * do carrinho nesta leitura por terem sido desativados.
 *
 * @param availability vazio quando não deu pra consultar o product-registration
 *                     — nesse caso nenhum item é tratado como indisponível.
 */
public record CartView(Cart cart, Map<Long, StockAvailability> availability, List<String> removedItems) {

    public static CartView withoutAvailability(Cart cart) {
        return new CartView(cart, Map.of(), List.of());
    }

    public StockAvailability availabilityOf(Long productId) {
        return availability.get(productId);
    }

    public boolean isUnavailable(Long productId) {
        return availabilityOf(productId) == StockAvailability.UNAVAILABLE;
    }

    /** Total só dos itens que podem ser comprados agora. */
    public double total() {
        return cart.getItems().stream()
                .filter(item -> !isUnavailable(item.getProductId()))
                .mapToDouble(CartItem::subtotal)
                .sum();
    }
}
