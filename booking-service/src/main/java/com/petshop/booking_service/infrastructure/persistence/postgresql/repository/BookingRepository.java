package com.petshop.booking_service.infrastructure.persistence.postgresql.repository;

import com.petshop.booking_service.core.domain.enums.StatusBooking;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.infrastructure.persistence.postgresql.entity.BookingEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {


    @Query("""
        SELECT b FROM BookingEntity b
        WHERE (:#{#criteria.petId} IS NULL OR b.petId = :#{#criteria.petId})
        AND (:#{#criteria.ownerCpf} IS NULL OR b.ownerCpf = :#{#criteria.ownerCpf})
        AND (:#{#criteria.serviceType} IS NULL OR b.serviceDetails.serviceType = :#{#criteria.serviceType})
        AND (:#{#criteria.status} IS NULL OR b.status = :#{#criteria.status})
        AND (:#{#criteria.employeeName} IS NULL OR b.employeeName = :#{#criteria.employeeName})
        AND (CAST(:startOfDay AS timestamp) IS NULL OR (b.bookingDateTime >= :startOfDay AND b.bookingDateTime < :endOfDay))
    """)
    Page<BookingEntity> findByCriteria(@Param("criteria") BookingSearchCriteriaDto criteria,
                                        @Param("startOfDay") LocalDateTime startOfDay,
                                        @Param("endOfDay") LocalDateTime endOfDay,
                                        Pageable pageable);

    List<BookingEntity> findByBookingDateTimeAndStatusNot(LocalDateTime bookingDateTime, StatusBooking status);

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(:namespace, :slotKey)", nativeQuery = true)
    Integer lockSlot(@Param("namespace") int namespace, @Param("slotKey") int slotKey);

    List<BookingEntity> findByPetIdAndBookingDateTimeBetweenAndStatusNot(
            UUID petId, LocalDateTime start, LocalDateTime end, StatusBooking status);
}
