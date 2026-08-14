package com.petshop.customermanagement.infrastructure.persistence.postgresql.mapper;

import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.StaffEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface StaffMapper {

    Staff toDomain(StaffEntity entity);

    StaffEntity toEntity(Staff domain);

    List<Staff> toDomainList(List<StaffEntity> entities);
}
