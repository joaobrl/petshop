package com.petshop.customermanagement.api.rest.dto;

import com.petshop.customermanagement.core.domain.BookingHistory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BookingHistoryResponseDtoTest {

    private BookingHistory historyWithDate(String bookingDate) {
        var history = new BookingHistory();
        history.setBookingId(UUID.randomUUID());
        history.setCustomerId(UUID.randomUUID());
        history.setServiceType("Banho");
        history.setBookingDate(bookingDate);
        history.setBookingTime("14:00");
        history.setStatus("CONFIRMED");
        return history;
    }

    @Test
    @DisplayName("Deve converter a data armazenada dd/MM/yyyy para yyyy-MM-dd e manter os demais campos")
    void shouldConvertStoredDateToIsoWhenFormatIsBrazilian() {
        var history = historyWithDate("10/08/2026");

        var dto = new BookingHistoryResponseDto(history);

        assertThat(dto.bookingId()).isEqualTo(history.getBookingId());
        assertThat(dto.customerId()).isEqualTo(history.getCustomerId());
        assertThat(dto.serviceType()).isEqualTo("Banho");
        assertThat(dto.bookingDate()).isEqualTo("2026-08-10");
        assertThat(dto.bookingTime()).isEqualTo("14:00");
        assertThat(dto.status()).isEqualTo("CONFIRMED");
    }

    @Test
    @DisplayName("Deve devolver nulo quando a data armazenada for nula")
    void shouldReturnNullDateWhenStoredDateIsNull() {
        var dto = new BookingHistoryResponseDto(historyWithDate(null));

        assertThat(dto.bookingDate()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"2026-08-10, 2026-08-10", "lixo, lixo", "99/99/2026, 99/99/2026"})
    @DisplayName("Deve devolver a data como está quando já for ISO ou for inválida")
    void shouldReturnDateAsIsWhenAlreadyIsoOrInvalid(String stored, String expected) {
        var dto = new BookingHistoryResponseDto(historyWithDate(stored));

        assertThat(dto.bookingDate()).isEqualTo(expected);
    }
}
