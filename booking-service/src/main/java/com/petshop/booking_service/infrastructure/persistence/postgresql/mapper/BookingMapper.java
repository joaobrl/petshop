package com.petshop.booking_service.infrastructure.persistence.postgresql.mapper;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.infrastructure.persistence.postgresql.entity.BookingEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface BookingMapper {

    Booking toDomain(BookingEntity entity);

    BookingEntity toEntity(Booking domain);

    List<Booking> toDomainList(List<BookingEntity> entities);
}
