package com.petshop.customermanagement.infrastructure.persistence.mongo.mapper;

import com.petshop.customermanagement.core.domain.PurchaseHistory;
import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerPurchaseHistory;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface PurchaseHistoryMapper {

    PurchaseHistory toDomain(CustomerPurchaseHistory entity);

    @Mapping(target = "id", ignore = true)
    CustomerPurchaseHistory toEntity(PurchaseHistory domain);
}
