package com.petshop.booking_service.infrastructure.persistence.postgresql.adapter;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.core.port.out.BookingPortOut;
import com.petshop.booking_service.infrastructure.persistence.postgresql.mapper.BookingMapper;
import com.petshop.booking_service.infrastructure.persistence.postgresql.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BookingPersistenceAdapterOut implements BookingPortOut {

    private final BookingRepository bookingRepository;
    private final BookingMapper mapper;

    @Override
    public Booking save(Booking booking) {
        var entity = mapper.toEntity(booking);
        return mapper.toDomain(bookingRepository.save(entity));
    }

    @Override
    public List<Booking> findByCriteria(BookingSearchCriteriaDto criteria) {
        // Filtro de data vira intervalo [inícioDoDia, inícioDoDiaSeguinte) em vez de CAST(bookingDateTime AS date)
        // — ver BookingRepository.findByCriteria pro motivo (CAST não é confiável aqui com Postgres + Hibernate).
        var date = criteria.getDate();
        var startOfDay = date != null ? date.atStartOfDay() : null;
        var endOfDay = date != null ? date.plusDays(1).atStartOfDay() : null;
        return mapper.toDomainList(bookingRepository.findByCriteria(criteria, startOfDay, endOfDay));
    }

    @Override
    public Optional<Booking> findById(UUID id) {
        return bookingRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public List<Booking> findActiveBookingsAt(LocalDateTime dateTime) {
        return mapper.toDomainList(bookingRepository.findByBookingDateTimeAndStatusNot(dateTime, StatusBooking.CANCELED));
    }

    @Override
    public List<Booking> findActiveBookingsForPetOnDate(UUID petId, LocalDate date) {
        var startOfDay = date.atStartOfDay();
        var endOfDay = date.plusDays(1).atStartOfDay();
        return mapper.toDomainList(bookingRepository.findByPetIdAndBookingDateTimeBetweenAndStatusNot(
                petId, startOfDay, endOfDay, StatusBooking.CANCELED));
    }
}
