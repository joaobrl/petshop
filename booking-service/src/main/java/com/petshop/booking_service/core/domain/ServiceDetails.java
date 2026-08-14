package com.petshop.booking_service.core.domain;

import com.petshop.booking_service.core.domain.enums.PaymentMethod;
import com.petshop.booking_service.core.domain.enums.PaymentStatus;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.commons.exception.BusinessRuleException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceDetails {

    private ServiceType serviceType;

    private BigDecimal price;
    private Integer durationInMinutes;

    private PaymentStatus paymentStatus;
    private PaymentMethod paymentMethod;

    public ServiceDetails(ServiceType serviceType) {
        this.serviceType = serviceType;
        switch (serviceType) {
            case TOSAGEM -> {
                this.price = new BigDecimal("50.00");
                this.durationInMinutes = 30;
            }
            case BANHO -> {
                this.price = new BigDecimal("80.00");
                this.durationInMinutes = 45;
            }
            case TOSA_E_BANHO -> {
                this.price = new BigDecimal("120.00");
                this.durationInMinutes = 60;
            }
            case CONSULTA_VETERINARIA -> {
                this.price = new BigDecimal("100.00");
                this.durationInMinutes = 30;
            }
        }

        this.paymentStatus = PaymentStatus.PENDING;
        this.paymentMethod = null;
    }

    /**
     * Confirma o pagamento presencial (pix/dinheiro/cartão) — sem relação com o pagamento simulado do order-service.
     */
    public void confirmPayment(PaymentMethod method) {
        if (method == null) {
            throw new IllegalArgumentException("Payment method cannot be null");
        }
        if (this.paymentStatus == PaymentStatus.COMPLETED) {
            throw new BusinessRuleException("Payment has already been confirmed for this booking");
        }
        this.paymentStatus = PaymentStatus.COMPLETED;
        this.paymentMethod = method;
    }
}