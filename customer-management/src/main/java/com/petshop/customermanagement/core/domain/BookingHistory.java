package com.petshop.customermanagement.core.domain;

import lombok.Data;

import java.util.UUID;

@Data
public class BookingHistory {
    private UUID bookingId;
    private UUID customerId;
    private String serviceType;
    private String bookingDate;
    private String bookingTime;
    private String status;
}