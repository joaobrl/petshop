package com.petshop.order_service.infrastructure.persistence.postgresql.repository;

import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface CartRepository extends JpaRepository<CartEntity, UUID> {
    // There's no DB constraint stopping two concurrent requests from each
    // creating an OPEN cart for the same customer (no unique index, no
    // locking in CartService.addItem's find-or-create) — a single-result
    // query here throws NonUniqueResultException the moment that happens.
    // Ordered-list + "take first" tolerates the duplicate instead of 500ing
    // every request for that customer from then on; it doesn't stop more
    // duplicates from being created, just stops them from being fatal.
    List<CartEntity> findByCustomerIdAndStatusOrderByCreatedAtDesc(UUID customerId, CartStatus status);

    List<CartEntity> findByStatusAndExpiresAtBefore(CartStatus status, LocalDateTime dateTime);
}
