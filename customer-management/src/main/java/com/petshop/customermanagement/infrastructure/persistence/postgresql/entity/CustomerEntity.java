package com.petshop.customermanagement.infrastructure.persistence.postgresql.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "Customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEntity implements Persistable<UUID> {

    // Id é gerado no domínio (Customer), não pelo banco/Hibernate. Sem
    // @GeneratedValue: o valor que chega aqui já é definitivo.
    @Id
    private UUID id;
    private String name;
    @Column(unique = true)
    private String cpf;
    @Column(unique = true)
    private String email;
    private String phone;
    private Boolean enabled;
    @ElementCollection
    @CollectionTable(name = "customer_pets", joinColumns = @JoinColumn(name = "customer_id"))
    private List<PetEmbeddable> pet = new ArrayList<>();
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "must_change_password")
    private Boolean mustChangePassword;

    // Como o id já vem preenchido (gerado no domínio) antes do primeiro
    // save(), o Spring Data não consegue usar "id == null" pra saber se é
    // um registro novo — sem isso ele chama merge() em vez de persist()
    // pra toda entidade nova, e o Hibernate acaba interpretando um insert
    // legítimo como um StaleObjectStateException (linha "já deletada por
    // outra transação"). Persistable resolve isso explicitamente.
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
