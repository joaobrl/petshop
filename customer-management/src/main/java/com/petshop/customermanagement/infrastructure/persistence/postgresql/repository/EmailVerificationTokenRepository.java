package com.petshop.customermanagement.infrastructure.persistence.postgresql.repository;

import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.EmailVerificationTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationTokenEntity, UUID> {

    Optional<EmailVerificationTokenEntity> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update EmailVerificationTokenEntity t set t.usedAt = :usedAt where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") UUID id, @Param("usedAt") Instant usedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from EmailVerificationTokenEntity t where t.customerId = :customerId and t.usedAt is null")
    int deleteUnusedByCustomerId(@Param("customerId") UUID customerId);
}
