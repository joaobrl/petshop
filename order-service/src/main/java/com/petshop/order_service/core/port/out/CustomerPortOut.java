package com.petshop.order_service.core.port.out;

import com.petshop.order_service.core.domain.CustomerInfo;

import java.util.Optional;
import java.util.UUID;

public interface CustomerPortOut {
    Optional<CustomerInfo> findCustomerById(UUID customerId);
}
