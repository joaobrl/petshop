package com.petshop.customermanagement.infrastructure.persistence.postgresql.mapper;

import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.CustomerEntity;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface CustomerMapper {

    Customer toDomain(CustomerEntity entity);

    CustomerEntity toEntity(Customer domain);

    List<Customer> toDomainList(List<CustomerEntity> entities);
}