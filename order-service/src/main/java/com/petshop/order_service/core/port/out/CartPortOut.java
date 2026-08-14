package com.petshop.order_service.core.port.out;

import com.petshop.order_service.core.domain.Cart;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartPortOut {
    Cart save(Cart cart);

    Optional<Cart> findOpenCartByCustomerId(UUID customerId);

    Optional<Cart> findById(UUID id);

    List<Cart> findExpiredOpenCarts();
}
