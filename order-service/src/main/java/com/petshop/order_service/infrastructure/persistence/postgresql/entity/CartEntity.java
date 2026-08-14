package com.petshop.order_service.infrastructure.persistence.postgresql.entity;

import com.petshop.order_service.core.domain.enums.CartStatus;
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
@Table(name = "carts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CartEntity implements Persistable<UUID> {

    // Id é gerado no domínio (Cart.openFor), não pelo banco/Hibernate. Sem
    // @GeneratedValue: o valor que chega aqui já é definitivo.
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID customerId;

    @ElementCollection
    @CollectionTable(name = "cart_items", joinColumns = @JoinColumn(name = "cart_id"))
    private List<CartItemEmbeddable> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    private CartStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

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
