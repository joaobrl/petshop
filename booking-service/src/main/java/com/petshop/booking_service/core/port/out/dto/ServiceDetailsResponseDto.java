package com.petshop.booking_service.core.port.out.dto;

import com.petshop.booking_service.core.domain.enums.PaymentMethod;
import com.petshop.booking_service.core.domain.enums.PaymentStatus;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ServiceDetailsResponseDto {

    private ServiceType serviceType;

    private BigDecimal price;

    private Integer durationInMinutes;

    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;
}
