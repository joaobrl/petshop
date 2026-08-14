package com.petshop.booking_service.core.domain.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTypeTest {

    @Test
    void hasExactlyFourValues() {
        assertThat(ServiceType.values()).containsExactly(
                ServiceType.TOSAGEM, ServiceType.BANHO, ServiceType.TOSA_E_BANHO, ServiceType.CONSULTA_VETERINARIA);
    }

    @ParameterizedTest(name = "{0} exige o cargo {1}")
    @CsvSource({
            "TOSAGEM, GROOMER",
            "BANHO, GROOMER",
            "TOSA_E_BANHO, GROOMER",
            "CONSULTA_VETERINARIA, VETERINARIAN"
    })
    void requiredStaffRoleMapsServiceToRole(ServiceType serviceType, String expectedRole) {
        assertThat(serviceType.requiredStaffRole()).isEqualTo(expectedRole);
    }
}
