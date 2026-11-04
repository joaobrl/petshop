package com.petshop.customermanagement.infrastructure.persistence.postgresql.repository;

import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.CustomerEntity;
import com.petshop.customermanagement.core.domain.Pet;
import com.petshop.customermanagement.core.domain.enums.PetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<CustomerEntity, UUID> {
    Optional<CustomerEntity> findByCpf(String cpf);
    Optional<CustomerEntity> findByEmail(String email);

    // Pets sao @ElementCollection (customer_pets): a query projeta direto nas
    // colunas da collection table, sem carregar CustomerEntity. O ORDER BY vem
    // do Pageable (propriedades qualificadas com "p.").
    @Query(value = """
            select new com.petshop.customermanagement.core.domain.Pet(
                p.id, p.petName, p.petType, p.petBreed, p.petSize, p.weightInKg, p.petHealthIssues)
            from CustomerEntity c join c.pet p
            where (:type is null or p.petType = :type)
            """,
            countQuery = """
            select count(p) from CustomerEntity c join c.pet p
            where (:type is null or p.petType = :type)
            """)
    Page<Pet> findPets(@Param("type") PetType type, Pageable pageable);

    @Query(value = """
            select new com.petshop.customermanagement.core.domain.Pet(
                p.id, p.petName, p.petType, p.petBreed, p.petSize, p.weightInKg, p.petHealthIssues)
            from CustomerEntity c join c.pet p
            where c.id = :customerId
            """,
            countQuery = """
            select count(p) from CustomerEntity c join c.pet p
            where c.id = :customerId
            """)
    Page<Pet> findPetsByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);
}
