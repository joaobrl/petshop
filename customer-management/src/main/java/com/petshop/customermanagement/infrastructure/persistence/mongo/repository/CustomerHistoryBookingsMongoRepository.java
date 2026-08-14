package com.petshop.customermanagement.infrastructure.persistence.mongo.repository;

import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerHistoryBookings;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerHistoryBookingsMongoRepository extends MongoRepository<CustomerHistoryBookings, String> {

    Optional<CustomerHistoryBookings> findByBookingId(UUID bookingId);

    List<CustomerHistoryBookings> findAllByCustomerId(UUID customerId);
}
