package com.petshop.customermanagement.infrastructure.persistence.mongo.repository;

import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerPurchaseHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPurchaseHistoryMongoRepository extends MongoRepository<CustomerPurchaseHistory, String> {

    Optional<CustomerPurchaseHistory> findByOrderId(UUID orderId);

    List<CustomerPurchaseHistory> findAllByCustomerId(UUID customerId);
}
