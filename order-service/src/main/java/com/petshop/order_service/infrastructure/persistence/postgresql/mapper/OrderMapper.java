package com.petshop.order_service.infrastructure.persistence.postgresql.mapper;

import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.OrderEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface OrderMapper {

    Order toDomain(OrderEntity entity);

    OrderEntity toEntity(Order domain);

    List<Order> toDomainList(List<OrderEntity> entities);
}
