package com.petshop.product_registration.infrastructure.persistence.mongo.repository;

import com.petshop.product_registration.infrastructure.persistence.mongo.entity.ProductDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductMongoRepository extends MongoRepository<ProductDocument, Long> {
    List<ProductDocument> findByEnabledTrue();
}
