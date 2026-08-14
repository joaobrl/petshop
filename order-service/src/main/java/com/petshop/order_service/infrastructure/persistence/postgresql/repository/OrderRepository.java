package com.petshop.order_service.infrastructure.persistence.postgresql.repository;

import com.petshop.order_service.core.domain.enums.OrderStatus;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    List<OrderEntity> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime dateTime);

    List<OrderEntity> findByStatusAndPickupReadyAtLessThanEqual(OrderStatus status, LocalDateTime dateTime);
}
