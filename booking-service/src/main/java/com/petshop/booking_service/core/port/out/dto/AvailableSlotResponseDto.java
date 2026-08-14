package com.petshop.booking_service.core.port.out.dto;

import com.petshop.booking_service.core.domain.AvailableSlot;
import lombok.Data;

import java.time.format.DateTimeFormatter;

@Data
public class AvailableSlotResponseDto {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final String dateTime;
    private final int availableStaff;

    public AvailableSlotResponseDto(AvailableSlot slot) {
        this.dateTime = slot.dateTime().format(FORMATTER);
        this.availableStaff = slot.availableStaff();
    }
}
