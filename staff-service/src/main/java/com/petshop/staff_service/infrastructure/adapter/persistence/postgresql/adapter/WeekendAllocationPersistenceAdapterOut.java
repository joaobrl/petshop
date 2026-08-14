package com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.adapter;

import com.petshop.staff_service.core.domain.WeekendAllocation;
import com.petshop.staff_service.core.port.out.WeekendAllocationPortOut;
import com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.mapper.WeekendAllocationMapper;
import com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.repository.WeekendAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WeekendAllocationPersistenceAdapterOut implements WeekendAllocationPortOut {

    private final WeekendAllocationRepository repository;
    private final WeekendAllocationMapper mapper;

    @Override
    public Optional<LocalDate> findLastAllocatedDate() {
        return repository.findLastAllocatedDate();
    }

    @Override
    public List<WeekendAllocation> findByDate(LocalDate date) {
        return mapper.toDomainList(repository.findByAllocationDate(date));
    }

    @Override
    public long countAll() {
        return repository.count();
    }

    @Override
    public List<WeekendAllocation> saveAll(List<WeekendAllocation> allocations) {
        var entities = mapper.toEntityList(allocations);
        return mapper.toDomainList(repository.saveAll(entities));
    }
}
