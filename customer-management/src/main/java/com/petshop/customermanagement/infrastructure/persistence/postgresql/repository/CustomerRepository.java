package com.petshop.customermanagement.infrastructure.persistence.postgresql.repository;

import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.CustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {
    Optional<CustomerEntity> findByCpf(String cpf);
    Optional<CustomerEntity> findByEmail(String email);
}
