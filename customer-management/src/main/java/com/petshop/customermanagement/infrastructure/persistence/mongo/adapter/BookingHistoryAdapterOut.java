package com.petshop.customermanagement.infrastructure.persistence.mongo.adapter;

import com.petshop.customermanagement.core.domain.BookingHistory;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerHistoryBookings;
import com.petshop.customermanagement.infrastructure.persistence.mongo.mapper.BookingHistoryMapper;
import com.petshop.customermanagement.infrastructure.persistence.mongo.repository.CustomerHistoryBookingsMongoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class BookingHistoryAdapterOut implements BookingHistoryPortOut {

    private final CustomerHistoryBookingsMongoRepository repository;
    private final BookingHistoryMapper mapper;

    @Override
    public Optional<BookingHistory> findByBookingId(UUID bookingId) {
        return repository.findByBookingId(bookingId)
                .map(mapper::toDomain);
    }

    @Override
    public List<BookingHistory> findAllByCustomerId(UUID customerId) {
        return repository.findAllByCustomerId(customerId).stream()
                .map(mapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Page<BookingHistory> findAll(Pageable pageable) {
        return repository.findAll(pageable).map(mapper::toDomain);
    }

    @Override
    public BookingHistory save(BookingHistory historyDomain) {
        CustomerHistoryBookings entity = mapper.toEntity(historyDomain);

        repository.findByBookingId(historyDomain.getBookingId())
                .ifPresent(existing -> entity.setId(existing.getId()));

        CustomerHistoryBookings savedEntity = repository.save(entity);
        return mapper.toDomain(savedEntity);
    }
}