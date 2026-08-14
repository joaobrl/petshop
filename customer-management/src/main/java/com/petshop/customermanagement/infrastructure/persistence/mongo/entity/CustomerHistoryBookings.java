package com.petshop.customermanagement.infrastructure.persistence.mongo.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Data
@Document(collection = "customer_history_bookings")
public class CustomerHistoryBookings {

    @Id
    private String id;

    private UUID customerId;
    private UUID bookingId;
    private String serviceType;
    private String bookingDate;
    private String bookingTime;
    private String status;
}
