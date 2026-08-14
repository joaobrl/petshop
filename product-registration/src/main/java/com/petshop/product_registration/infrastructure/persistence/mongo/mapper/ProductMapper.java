package com.petshop.product_registration.infrastructure.persistence.mongo.mapper;

import com.petshop.product_registration.core.domain.Product;
import com.petshop.product_registration.infrastructure.persistence.mongo.entity.ProductDocument;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ProductMapper {

    Product toDomain(ProductDocument entity);

    ProductDocument toEntity(Product domain);

    List<Product> toDomainList(List<ProductDocument> entities);
}
