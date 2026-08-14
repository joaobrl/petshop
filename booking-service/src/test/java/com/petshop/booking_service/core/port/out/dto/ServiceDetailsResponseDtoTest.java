package com.petshop.booking_service.core.port.out.dto;

import com.petshop.booking_service.core.domain.enums.PaymentMethod;
import com.petshop.booking_service.core.domain.enums.PaymentStatus;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceDetailsResponseDtoTest {

    @Test
    void settersAndGettersRoundTrip() {
        var dto = new ServiceDetailsResponseDto();
        dto.setServiceType(ServiceType.BANHO);
        dto.setPrice(new BigDecimal("80.00"));
        dto.setDurationInMinutes(45);
        dto.setPaymentStatus(PaymentStatus.COMPLETED);
        dto.setPaymentMethod(PaymentMethod.PIX);

        assertThat(dto.getServiceType()).isEqualTo(ServiceType.BANHO);
        assertThat(dto.getPrice()).isEqualByComparingTo("80.00");
        assertThat(dto.getDurationInMinutes()).isEqualTo(45);
        assertThat(dto.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(dto.getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var a = new ServiceDetailsResponseDto();
        a.setServiceType(ServiceType.BANHO);
        var b = new ServiceDetailsResponseDto();
        b.setServiceType(ServiceType.BANHO);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
