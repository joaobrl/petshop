package com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.mapper;

import com.petshop.staff_service.core.domain.WeekendAllocation;
import com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.entity.WeekendAllocationEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface WeekendAllocationMapper {

    WeekendAllocation toDomain(WeekendAllocationEntity entity);

    WeekendAllocationEntity toEntity(WeekendAllocation domain);

    List<WeekendAllocation> toDomainList(List<WeekendAllocationEntity> entities);

    List<WeekendAllocationEntity> toEntityList(List<WeekendAllocation> domains);
}
