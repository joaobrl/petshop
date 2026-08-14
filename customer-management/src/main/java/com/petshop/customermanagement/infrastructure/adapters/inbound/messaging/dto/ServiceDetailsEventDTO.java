package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto;

import java.math.BigDecimal;

public record ServiceDetailsEventDTO(
        String serviceType,
        BigDecimal price,
        Integer durationInMinutes
) {
}
