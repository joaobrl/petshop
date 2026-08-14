package com.petshop.booking_service.infrastructure.persistence.postgresql.adapter;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.infrastructure.persistence.postgresql.entity.BookingEntity;
import com.petshop.booking_service.infrastructure.persistence.postgresql.mapper.BookingMapper;
import com.petshop.booking_service.infrastructure.persistence.postgresql.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingPersistenceAdapterOutTest {

    @Mock
    private BookingRepository repository;

    @Mock
    private BookingMapper mapper;

    private BookingPersistenceAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new BookingPersistenceAdapterOut(repository, mapper);
    }

    @Test
    void saveDelegatesToRepository() {
        var booking = new Booking();
        var entity = new BookingEntity();
        when(mapper.toEntity(booking)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(booking);

        assertThat(adapter.save(booking)).isEqualTo(booking);
    }

    @Test
    void findByCriteriaConvertsDateToDayRange() {
        var date = LocalDate.of(2026, 8, 15);
        var criteria = new BookingSearchCriteriaDto(null, null, null, null, date, null);
        var pageable = PageRequest.of(0, 20);
        var entity = new BookingEntity();
        var booking = new Booking();
        when(repository.findByCriteria(criteria, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), pageable))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(mapper.toDomain(entity)).thenReturn(booking);

        var result = adapter.findByCriteria(criteria, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(repository).findByCriteria(criteria, date.atStartOfDay(), date.plusDays(1).atStartOfDay(), pageable);
    }

    @Test
    void findByCriteriaWithNullDatePassesNullRange() {
        var criteria = new BookingSearchCriteriaDto(null, null, null, null, null, null);
        var pageable = PageRequest.of(0, 20);
        when(repository.findByCriteria(criteria, null, null, pageable)).thenReturn(new PageImpl<>(List.of()));

        var result = adapter.findByCriteria(criteria, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(repository).findByCriteria(criteria, null, null, pageable);
    }

    @Test
    void findByIdDelegatesToRepository() {
        var id = UUID.randomUUID();
        var booking = new Booking();
        var entity = new BookingEntity();
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(booking);

        assertThat(adapter.findById(id)).contains(booking);
    }

    @Test
    void findActiveBookingsAtDelegatesToRepositoryExcludingCanceled() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var entities = List.of(new BookingEntity());
        when(repository.findByBookingDateTimeAndStatusNot(dateTime, StatusBooking.CANCELED))
                .thenReturn(entities);
        when(mapper.toDomainList(entities)).thenReturn(List.of(new Booking()));

        var result = adapter.findActiveBookingsAt(dateTime);

        assertThat(result).hasSize(1);
    }

    @Test
    void findActiveBookingsForPetOnDateConvertsToDayRangeExcludingCanceled() {
        var petId = UUID.randomUUID();
        var date = LocalDate.of(2026, 8, 15);
        var startOfDay = date.atStartOfDay();
        var endOfDay = date.plusDays(1).atStartOfDay();
        var entities = List.of(new BookingEntity());
        when(repository.findByPetIdAndBookingDateTimeBetweenAndStatusNot(petId, startOfDay, endOfDay, StatusBooking.CANCELED))
                .thenReturn(entities);
        when(mapper.toDomainList(entities)).thenReturn(List.of(new Booking()));

        var result = adapter.findActiveBookingsForPetOnDate(petId, date);

        assertThat(result).hasSize(1);
        verify(repository).findByPetIdAndBookingDateTimeBetweenAndStatusNot(petId, startOfDay, endOfDay, StatusBooking.CANCELED);
    }
}
