package com.petshop.booking_service.core.domain;

import com.petshop.booking_service.core.domain.enums.PaymentMethod;
import com.petshop.booking_service.core.domain.enums.PaymentStatus;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.commons.exception.BusinessRuleException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceDetailsTest {

    @ParameterizedTest(name = "{0} custa {1} e dura {2} minutos")
    @CsvSource({
            "TOSAGEM, 50.00, 30",
            "BANHO, 80.00, 45",
            "TOSA_E_BANHO, 120.00, 60",
            "CONSULTA_VETERINARIA, 100.00, 30"
    })
    void derivesPriceAndDurationFromServiceType(ServiceType serviceType, String price, int duration) {
        var details = new ServiceDetails(serviceType);

        assertThat(details.getServiceType()).isEqualTo(serviceType);
        assertThat(details.getPrice()).isEqualByComparingTo(new BigDecimal(price));
        assertThat(details.getDurationInMinutes()).isEqualTo(duration);
    }

    @Test
    void convenienceConstructorStartsPaymentAsPending() {
        var details = new ServiceDetails(ServiceType.BANHO);

        assertThat(details.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(details.getPaymentMethod()).isNull();
    }

    @Test
    void allArgsConstructorAndSettersRoundTrip() {
        var details = new ServiceDetails(ServiceType.BANHO, new BigDecimal("99.90"), 40, PaymentStatus.PENDING, null);

        assertThat(details.getServiceType()).isEqualTo(ServiceType.BANHO);
        assertThat(details.getPrice()).isEqualByComparingTo("99.90");
        assertThat(details.getDurationInMinutes()).isEqualTo(40);
        assertThat(details.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(details.getPaymentMethod()).isNull();

        details.setPrice(new BigDecimal("10.00"));
        assertThat(details.getPrice()).isEqualByComparingTo("10.00");
    }

    @Test
    void noArgsConstructorCreatesEmptyInstance() {
        var details = new ServiceDetails();

        assertThat(details.getServiceType()).isNull();
        assertThat(details.getPrice()).isNull();
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var a = new ServiceDetails(ServiceType.TOSAGEM);
        var b = new ServiceDetails(ServiceType.TOSAGEM);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Nested
    class ConfirmPayment {

        @Test
        void confirmsPaymentWithGivenMethod() {
            var details = new ServiceDetails(ServiceType.BANHO);

            details.confirmPayment(PaymentMethod.PIX);

            assertThat(details.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(details.getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
        }

        @Test
        void rejectsNullPaymentMethod() {
            var details = new ServiceDetails(ServiceType.BANHO);

            assertThatThrownBy(() -> details.confirmPayment(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Payment method cannot be null");
        }

        @Test
        void rejectsConfirmingAnAlreadyCompletedPayment() {
            var details = new ServiceDetails(ServiceType.BANHO);
            details.confirmPayment(PaymentMethod.DINHEIRO);

            assertThatThrownBy(() -> details.confirmPayment(PaymentMethod.CARTAO))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Payment has already been confirmed for this booking");
        }
    }
}
