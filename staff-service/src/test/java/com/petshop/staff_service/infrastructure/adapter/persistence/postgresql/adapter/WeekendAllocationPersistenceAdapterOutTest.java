package com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.adapter;

import com.petshop.staff_service.core.domain.WeekendAllocation;
import com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.entity.WeekendAllocationEntity;
import com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.mapper.WeekendAllocationMapper;
import com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.repository.WeekendAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeekendAllocationPersistenceAdapterOutTest {

    @Mock
    private WeekendAllocationRepository repository;

    @Mock
    private WeekendAllocationMapper mapper;

    private WeekendAllocationPersistenceAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new WeekendAllocationPersistenceAdapterOut(repository, mapper);
    }

    @Test
    void findLastAllocatedDateDelegatesToRepository() {
        var date = LocalDate.of(2026, 8, 15);
        when(repository.findLastAllocatedDate()).thenReturn(Optional.of(date));

        var result = adapter.findLastAllocatedDate();

        assertThat(result).contains(date);
    }

    @Test
    void findByDateDelegatesToRepository() {
        var date = LocalDate.of(2026, 8, 15);
        var entity = new WeekendAllocationEntity();
        var domain = new WeekendAllocation();
        when(repository.findByAllocationDate(date)).thenReturn(List.of(entity));
        when(mapper.toDomainList(List.of(entity))).thenReturn(List.of(domain));

        var result = adapter.findByDate(date);

        assertThat(result).containsExactly(domain);
    }

    @Test
    void countAllDelegatesToRepository() {
        when(repository.count()).thenReturn(5L);

        assertThat(adapter.countAll()).isEqualTo(5L);
    }

    @Test
    void saveAllDelegatesToRepository() {
        var domains = List.of(new WeekendAllocation());
        var entities = List.of(new WeekendAllocationEntity());
        when(mapper.toEntityList(domains)).thenReturn(entities);
        when(repository.saveAll(entities)).thenReturn(entities);
        when(mapper.toDomainList(entities)).thenReturn(domains);

        var result = adapter.saveAll(domains);

        assertThat(result).isEqualTo(domains);
        verify(repository).saveAll(entities);
    }
}
