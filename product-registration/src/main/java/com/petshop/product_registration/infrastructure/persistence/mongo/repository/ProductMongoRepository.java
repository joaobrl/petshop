package com.petshop.product_registration.infrastructure.persistence.mongo.repository;

import com.petshop.product_registration.infrastructure.persistence.mongo.entity.ProductDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProductMongoRepository extends MongoRepository<ProductDocument, Long> {
    Page<ProductDocument> findByEnabledTrue(Pageable pageable);
}
