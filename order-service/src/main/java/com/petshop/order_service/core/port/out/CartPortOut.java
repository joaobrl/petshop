package com.petshop.order_service.core.port.out;

import com.petshop.order_service.core.domain.Cart;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartPortOut {
    Cart save(Cart cart);

    Optional<Cart> findOpenCartByCustomerId(UUID customerId);

    /**
     * Mesmo que {@link #findOpenCartByCustomerId}, mas com lock pessimista
     * ({@code SELECT ... FOR UPDATE}) até o fim da transação — usado só pelo
     * checkout, para dois checkouts simultâneos do mesmo cliente rodarem em
     * fila: o segundo só lê o carrinho depois que o primeiro commitou.
     */
    Optional<Cart> findOpenCartByCustomerIdForUpdate(UUID customerId);

    Optional<Cart> findById(UUID id);

    List<Cart> findExpiredOpenCarts();
}
