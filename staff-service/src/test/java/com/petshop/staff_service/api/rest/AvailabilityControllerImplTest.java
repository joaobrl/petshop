package com.petshop.staff_service.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.staff_service.core.domain.AvailabilityResult;
import com.petshop.staff_service.core.domain.Staff;
import com.petshop.staff_service.core.port.in.AvailabilityPortIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityControllerImplTest {

    @Mock
    private AvailabilityPortIn portIn;

    private AvailabilityControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new AvailabilityControllerImpl(portIn);
    }

    @Test
    void getGeneralAvailabilityReturnsOkWithMappedDto() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var result = new AvailabilityResult(2, true, dateTime);
        when(portIn.checkGeneralAvailability(dateTime, Role.GROOMER)).thenReturn(result);

        var response = controller.getGeneralAvailability(dateTime, Role.GROOMER);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getAvailableSlots()).isEqualTo(2);
        assertThat(response.getBody().getIsAvailable()).isTrue();
    }

    @Test
    void getScheduleReturnsOkWithMappedStaffList() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var staff = new Staff(UUID.randomUUID(), "Ciclana", "12345678900", "ciclana@petshop.com", "11999990000", true, Role.GROOMER);
        when(portIn.scheduledStaffFor(dateTime, Role.GROOMER)).thenReturn(List.of(staff));

        var response = controller.getSchedule(dateTime, Role.GROOMER);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getName()).isEqualTo("Ciclana");
    }

    @Test
    void getScheduleReturnsEmptyListWhenNoStaffScheduled() {
        var dateTime = LocalDateTime.of(2026, 8, 16, 9, 0);
        when(portIn.scheduledStaffFor(dateTime, Role.GROOMER)).thenReturn(List.of());

        var response = controller.getSchedule(dateTime, Role.GROOMER);

        assertThat(response.getBody()).isEmpty();
    }
}
