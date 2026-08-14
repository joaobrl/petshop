package com.petshop.booking_service.infrastructure.persistence.postgresql.repository;

import com.petshop.booking_service.core.domain.enums.StatusBooking;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.infrastructure.persistence.postgresql.entity.BookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<BookingEntity, UUID> {

    // Filtro de data compara bookingDateTime direto contra um intervalo [inícioDoDia, inícioDoDiaSeguinte) em vez
    // de CAST(bookingDateTime AS date) = CAST(:date AS date): com bind nulo, o Postgres não consegue inferir o
    // tipo do parâmetro isolado em "? IS NULL" nem dentro de um CAST via SpEL ("could not determine data type of
    // parameter"). O CAST(:startOfDay AS timestamp) abaixo resolve esse mesmo problema pro @Param startOfDay,
    // já que aqui (diferente das expressões SpEL :#{#criteria.xxx}) o Hibernate não infere o tipo sozinho.
    @Query("""
        SELECT b FROM BookingEntity b
        WHERE (:#{#criteria.petId} IS NULL OR b.petId = :#{#criteria.petId})
        AND (:#{#criteria.ownerCpf} IS NULL OR b.ownerCpf = :#{#criteria.ownerCpf})
        AND (:#{#criteria.serviceType} IS NULL OR b.serviceDetails.serviceType = :#{#criteria.serviceType})
        AND (:#{#criteria.status} IS NULL OR b.status = :#{#criteria.status})
        AND (:#{#criteria.employeeName} IS NULL OR b.employeeName = :#{#criteria.employeeName})
        AND (CAST(:startOfDay AS timestamp) IS NULL OR (b.bookingDateTime >= :startOfDay AND b.bookingDateTime < :endOfDay))
    """)
    List<BookingEntity> findByCriteria(@Param("criteria") BookingSearchCriteriaDto criteria,
                                        @Param("startOfDay") LocalDateTime startOfDay,
                                        @Param("endOfDay") LocalDateTime endOfDay);

    List<BookingEntity> findByBookingDateTimeAndStatusNot(LocalDateTime bookingDateTime, StatusBooking status);

    List<BookingEntity> findByPetIdAndBookingDateTimeBetweenAndStatusNot(
            UUID petId, LocalDateTime start, LocalDateTime end, StatusBooking status);
}
