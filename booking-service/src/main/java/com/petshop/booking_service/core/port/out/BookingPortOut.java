package com.petshop.booking_service.core.port.out;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingPortOut {
    Booking save(Booking booking);
    Page<Booking> findByCriteria(BookingSearchCriteriaDto criteria, Pageable pageable);
    Optional<Booking> findById(UUID id);

    List<Booking> findActiveBookingsAt(LocalDateTime dateTime);

    void lockSlot(LocalDateTime dateTime);

    List<Booking> findActiveBookingsForPetOnDate(UUID petId, LocalDate date);
}