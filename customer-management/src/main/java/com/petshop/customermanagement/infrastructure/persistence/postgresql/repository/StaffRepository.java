package com.petshop.customermanagement.infrastructure.persistence.postgresql.repository;

import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.StaffEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<StaffEntity, UUID> {
    Optional<StaffEntity> findByCpf(String cpf);
    Optional<StaffEntity> findByEmail(String email);
}
