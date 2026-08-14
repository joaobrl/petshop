package com.petshop.booking_service.core.port.in.dto;

import com.petshop.booking_service.core.domain.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentConfirmationRequestDto {

    @NotNull
    private PaymentMethod paymentMethod;
}
