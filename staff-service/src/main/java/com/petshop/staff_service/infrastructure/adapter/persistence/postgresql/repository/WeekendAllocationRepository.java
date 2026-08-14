package com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.repository;

import com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.entity.WeekendAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeekendAllocationRepository extends JpaRepository<WeekendAllocationEntity, UUID> {

    List<WeekendAllocationEntity> findByAllocationDate(LocalDate allocationDate);

    @Query("SELECT MAX(w.allocationDate) FROM WeekendAllocationEntity w")
    Optional<LocalDate> findLastAllocatedDate();
}
