package com.petshop.customermanagement.infrastructure.persistence.postgresql.entity;

import com.petshop.commons.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.util.UUID;

/**
 * Mesmo raciocínio de id/Persistable do CustomerEntity: id é gerado no
 * domínio (Staff), não pelo Hibernate — sem isso, todo save() de um
 * Staff já existente tentaria persist() num id que já existe. Ver
 * comentário completo em CustomerEntity.
 */
@Entity
@Table(name = "staff")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StaffEntity implements Persistable<UUID> {

    @Id
    private UUID id;
    private String name;
    @Column(unique = true)
    private String cpf;
    @Column(unique = true)
    private String email;
    private String phone;
    private Boolean enabled;
    @Enumerated(EnumType.STRING)
    private Role role;
    @Column(name = "password_hash")
    private String passwordHash;
    @Column(name = "must_change_password")
    private Boolean mustChangePassword;

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
