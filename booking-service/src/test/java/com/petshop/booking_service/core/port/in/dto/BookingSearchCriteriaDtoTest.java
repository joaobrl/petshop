package com.petshop.booking_service.core.port.in.dto;

import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingSearchCriteriaDtoTest {

    @Test
    void parsesValidServiceTypeAndStatusCaseInsensitively() {
        var petId = UUID.randomUUID();
        var date = LocalDate.of(2026, 8, 15);

        var criteria = new BookingSearchCriteriaDto("12345678900", petId, "banho", "scheduled", date, "Ciclana");

        assertThat(criteria.getOwnerCpf()).isEqualTo("12345678900");
        assertThat(criteria.getPetId()).isEqualTo(petId);
        assertThat(criteria.getDate()).isEqualTo(date);
        assertThat(criteria.getEmployeeName()).isEqualTo("Ciclana");
        assertThat(criteria.getServiceType()).isEqualTo(ServiceType.BANHO);
        assertThat(criteria.getStatus()).isEqualTo(StatusBooking.SCHEDULED);
    }

    @Test
    void leavesServiceTypeAndStatusNullWhenNotProvided() {
        var criteria = new BookingSearchCriteriaDto(null, null, null, null, null, null);

        assertThat(criteria.getServiceType()).isNull();
        assertThat(criteria.getStatus()).isNull();
    }

    @Test
    void leavesServiceTypeAndStatusNullWhenBlank() {
        var criteria = new BookingSearchCriteriaDto(null, null, "   ", "   ", null, null);

        assertThat(criteria.getServiceType()).isNull();
        assertThat(criteria.getStatus()).isNull();
    }

    @Test
    void rejectsInvalidServiceType() {
        assertThatThrownBy(() -> new BookingSearchCriteriaDto(null, null, "INVALIDO", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tipo de serviço inválido: INVALIDO");
    }

    @Test
    void rejectsInvalidStatus() {
        assertThatThrownBy(() -> new BookingSearchCriteriaDto(null, null, null, "INVALIDO", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Status inválido: INVALIDO");
    }

    @Test
    void settersRoundTrip() {
        var criteria = new BookingSearchCriteriaDto(null, null, null, null, null, null);
        criteria.setOwnerCpf("99988877766");

        assertThat(criteria.getOwnerCpf()).isEqualTo("99988877766");
    }
}
