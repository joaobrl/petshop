package com.petshop.booking_service.infrastructure.persistence.postgresql.adapter;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.core.port.out.BookingPortOut;
import com.petshop.booking_service.infrastructure.persistence.postgresql.mapper.BookingMapper;
import com.petshop.booking_service.infrastructure.persistence.postgresql.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BookingPersistenceAdapterOut implements BookingPortOut {

    // Primeira metade da chave do advisory lock (pg_advisory_xact_lock(int, int)): separa os locks de horário de
    // agendamento de qualquer outro advisory lock que venha a usar o mesmo banco.
    static final int SLOT_LOCK_NAMESPACE = 5001;

    private final BookingRepository bookingRepository;
    private final BookingMapper mapper;

    @Override
    public Booking save(Booking booking) {
        var entity = mapper.toEntity(booking);
        return mapper.toDomain(bookingRepository.save(entity));
    }

    @Override
    public Page<Booking> findByCriteria(BookingSearchCriteriaDto criteria, Pageable pageable) {
        // Filtro de data vira intervalo [inícioDoDia, inícioDoDiaSeguinte) em vez de CAST(bookingDateTime AS date)
        // — ver BookingRepository.findByCriteria pro motivo (CAST não é confiável aqui com Postgres + Hibernate).
        var date = criteria.getDate();
        var startOfDay = date != null ? date.atStartOfDay() : null;
        var endOfDay = date != null ? date.plusDays(1).atStartOfDay() : null;
        return bookingRepository.findByCriteria(criteria, startOfDay, endOfDay, pageable).map(mapper::toDomain);
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
    public void lockSlot(LocalDateTime dateTime) {
        // Chave = minutos desde a epoch (o horário é um LocalDateTime "de parede", então UTC aqui é só uma
        // referência fixa pra conversão, não um fuso). Cabe em int até o ano ~6000.
        var slotKey = Math.toIntExact(dateTime.toEpochSecond(ZoneOffset.UTC) / 60);
        bookingRepository.lockSlot(SLOT_LOCK_NAMESPACE, slotKey);
    }

    @Override
    public List<Booking> findActiveBookingsForPetOnDate(UUID petId, LocalDate date) {
        var startOfDay = date.atStartOfDay();
        var endOfDay = date.plusDays(1).atStartOfDay();
        return mapper.toDomainList(bookingRepository.findByPetIdAndBookingDateTimeBetweenAndStatusNot(
                petId, startOfDay, endOfDay, StatusBooking.CANCELED));
    }
}
