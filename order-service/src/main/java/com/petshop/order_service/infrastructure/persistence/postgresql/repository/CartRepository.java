package com.petshop.order_service.infrastructure.persistence.postgresql.repository;

import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.CartEntity;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

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

    // Variante com lock pra o checkout (SELECT ... FOR UPDATE). O timeout
    // (10 s) fica acima do readTimeout do Feign (5 s): o lock é mantido
    // enquanto o checkout reserva estoque no product-registration.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("SELECT c FROM CartEntity c WHERE c.customerId = :customerId AND c.status = :status ORDER BY c.createdAt DESC")
    List<CartEntity> findForUpdateByCustomerIdAndStatus(@Param("customerId") UUID customerId, @Param("status") CartStatus status);

    List<CartEntity> findByStatusAndExpiresAtBefore(CartStatus status, LocalDateTime dateTime);
}
