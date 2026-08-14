package com.petshop.order_service.infrastructure.persistence.postgresql.mapper;

import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.CartEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CartMapper {

    Cart toDomain(CartEntity entity);

    CartEntity toEntity(Cart domain);

    List<Cart> toDomainList(List<CartEntity> entities);
}
