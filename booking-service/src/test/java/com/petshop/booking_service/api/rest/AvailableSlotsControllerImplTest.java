package com.petshop.booking_service.api.rest;

import com.petshop.booking_service.core.domain.AvailableSlot;
import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.port.in.AvailableSlotsPortIn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailableSlotsControllerImplTest {

    @Mock
    private AvailableSlotsPortIn portIn;

    private AvailableSlotsControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new AvailableSlotsControllerImpl(portIn);
    }

    @Test
    void returnsOkWithMappedSlots() {
        var date = LocalDate.of(2026, 8, 15);
        var slot = new AvailableSlot(LocalDateTime.of(2026, 8, 15, 9, 0), 2);
        when(portIn.findAvailableSlots(ServiceType.BANHO, date, RangeType.DAY)).thenReturn(List.of(slot));

        var response = controller.getAvailableSlots(ServiceType.BANHO, date, RangeType.DAY);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).getAvailableStaff()).isEqualTo(2);
    }

    @Test
    void returnsOkWithEmptyListWhenNoSlotsAvailable() {
        var date = LocalDate.of(2026, 8, 16);
        when(portIn.findAvailableSlots(ServiceType.BANHO, date, RangeType.DAY)).thenReturn(List.of());

        var response = controller.getAvailableSlots(ServiceType.BANHO, date, RangeType.DAY);

        assertThat(response.getBody()).isEmpty();
    }
}
