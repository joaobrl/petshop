package com.petshop.customermanagement.api.rest.dto;

import com.petshop.customermanagement.core.domain.BookingHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

public record BookingHistoryResponseDto(
        UUID bookingId,
        UUID customerId,
        String serviceType,
        @Schema(description = "Data em ISO-8601 (horário de Brasília)", example = "2026-08-15")
        String bookingDate,
        @Schema(description = "Hora em ISO-8601 (horário de Brasília)", example = "09:00")
        String bookingTime,
        String status
) {
    // O histórico no Mongo guarda a data como string no formato brasileiro (não
    // migrado — SDD-017); a conversão para ISO-8601 é feita só na saída.
    private static final DateTimeFormatter STORED_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public BookingHistoryResponseDto(BookingHistory history) {
        this(
                history.getBookingId(),
                history.getCustomerId(),
                history.getServiceType(),
                toIsoDate(history.getBookingDate()),
                history.getBookingTime(),
                history.getStatus()
        );
    }

    // HH:mm (bookingTime) já é ISO-8601; só a data precisa de conversão. Valor
    // fora do formato armazenado (nulo, já ISO ou inválido) sai como está.
    private static String toIsoDate(String storedDate) {
        if (storedDate == null) {
            return null;
        }
        try {
            return LocalDate.parse(storedDate, STORED_DATE_FORMAT).format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return storedDate;
        }
    }
}
