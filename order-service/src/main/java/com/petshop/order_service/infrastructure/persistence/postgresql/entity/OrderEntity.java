package com.petshop.order_service.infrastructure.persistence.postgresql.entity;

import com.petshop.order_service.core.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity implements Persistable<UUID> {

    // Id é gerado no domínio (Order.fromCart), não pelo banco/Hibernate.
    // Sem @GeneratedValue: o valor que chega aqui já é definitivo.
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @ElementCollection
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    private List<OrderItemEmbeddable> items = new ArrayList<>();

    private Double totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime paidAt;

    private LocalDateTime pickupReadyAt;

    // Id já vem preenchido (gerado no domínio) antes do save(), então o
    // Spring Data não pode usar "id == null" pra saber se é registro novo
    // — chamaria merge() em vez de persist() e o Hibernate lançaria
    // StaleObjectStateException num insert legítimo. Persistable resolve.
    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
