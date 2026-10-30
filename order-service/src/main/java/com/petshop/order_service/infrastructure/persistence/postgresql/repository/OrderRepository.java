package com.petshop.order_service.infrastructure.persistence.postgresql.repository;

import com.petshop.order_service.core.domain.enums.OrderStatus;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    List<OrderEntity> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime dateTime);

    List<OrderEntity> findByStatusAndPickupReadyAtLessThanEqual(OrderStatus status, LocalDateTime dateTime);

    List<OrderEntity> findByStatusAndExpiresAtBefore(OrderStatus status, LocalDateTime dateTime);

    // Pagamento e expiração disputam o mesmo pedido: o UPDATE condicional
    // trava a linha, e quem chegar depois reavalia o WHERE já com o status
    // novo e afeta 0 linhas — só uma das duas transições acontece.
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE OrderEntity o
               SET o.status = :paid, o.paidAt = :paidAt, o.pickupReadyAt = :pickupReadyAt
             WHERE o.id = :id
               AND o.status = :awaiting
               AND (o.expiresAt IS NULL OR o.expiresAt > :now)
            """)
    int markAsPaidIfAwaitingPayment(@Param("id") UUID id,
                                    @Param("paidAt") LocalDateTime paidAt,
                                    @Param("pickupReadyAt") LocalDateTime pickupReadyAt,
                                    @Param("now") LocalDateTime now,
                                    @Param("awaiting") OrderStatus awaiting,
                                    @Param("paid") OrderStatus paid);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE OrderEntity o SET o.status = :canceled WHERE o.id = :id AND o.status = :awaiting")
    int cancelIfAwaitingPayment(@Param("id") UUID id,
                                @Param("awaiting") OrderStatus awaiting,
                                @Param("canceled") OrderStatus canceled);
}
