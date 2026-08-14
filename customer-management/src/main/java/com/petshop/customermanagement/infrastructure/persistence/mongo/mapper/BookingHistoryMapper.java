package com.petshop.customermanagement.infrastructure.persistence.mongo.mapper;

import com.petshop.customermanagement.core.domain.BookingHistory;
import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerHistoryBookings;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface BookingHistoryMapper {

    BookingHistory toDomain(CustomerHistoryBookings entity);

    @Mapping(target = "id", ignore = true)
    CustomerHistoryBookings toEntity(BookingHistory domain);
}