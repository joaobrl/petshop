package com.petshop.order_service.infrastructure.persistence.postgresql.repository;

import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.CartEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<CartEntity, UUID> {
    Optional<CartEntity> findByCustomerIdAndStatus(UUID customerId, CartStatus status);

    List<CartEntity> findByStatusAndExpiresAtBefore(CartStatus status, LocalDateTime dateTime);
}
