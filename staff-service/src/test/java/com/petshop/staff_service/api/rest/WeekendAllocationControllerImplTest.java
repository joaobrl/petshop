package com.petshop.staff_service.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.staff_service.core.domain.Staff;
import com.petshop.staff_service.core.domain.WeekendAllocation;
import com.petshop.staff_service.core.port.in.WeekendAllocationPortIn;
import com.petshop.staff_service.core.port.out.WeekendAllocationPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeekendAllocationControllerImplTest {

    @Mock
    private WeekendAllocationPortIn portIn;
    @Mock
    private WeekendAllocationPortOut portOut;

    private WeekendAllocationControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new WeekendAllocationControllerImpl(portIn, portOut);
    }

    @Test
    void generateNextMonthReturnsOkWithMappedAllocations() {
        var staff = new Staff(UUID.randomUUID(), "Ciclana", "12345678900", "ciclana@petshop.com", "11999990000", true, Role.GROOMER);
        var allocation = new WeekendAllocation(LocalDate.of(2026, 8, 1), staff);
        when(portIn.generateNextMonth()).thenReturn(List.of(allocation));

        var response = controller.generateNextMonth();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getStaffName()).isEqualTo("Ciclana");
    }

    @Test
    void getAllocationsForDateReturnsOkWithMappedAllocations() {
        var date = LocalDate.of(2026, 8, 1);
        var staff = new Staff(UUID.randomUUID(), "Beltrano", "11122233344", "beltrano@petshop.com", "11988887777", true, Role.GROOMER);
        var allocation = new WeekendAllocation(date, staff);
        when(portOut.findByDate(date)).thenReturn(List.of(allocation));

        var response = controller.getAllocationsForDate(date);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getAllocationDate()).isEqualTo(date);
    }

    @Test
    void getAllocationsForDateReturnsEmptyListWhenNoneFound() {
        var date = LocalDate.of(2026, 9, 1);
        when(portOut.findByDate(date)).thenReturn(List.of());

        var response = controller.getAllocationsForDate(date);

        assertThat(response.getBody()).isEmpty();
    }
}
