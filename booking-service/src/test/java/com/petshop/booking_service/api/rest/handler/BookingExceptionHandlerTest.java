package com.petshop.booking_service.api.rest.handler;

import com.petshop.booking_service.core.domain.exception.BookingUnavailableException;
import com.petshop.commons.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BookingExceptionHandlerTest {

    private final BookingExceptionHandler handler = new BookingExceptionHandler();

    @Test
    void inheritsBaseExceptionHandlerBehavior() {
        var problem = handler.handleNotFound(new NotFoundException("Agendamento", "x"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
    }

    @Test
    void bookingUnavailableWithSuggestionIncludesFormattedSuggestedSlot() {
        var suggested = LocalDateTime.of(2026, 8, 10, 9, 30);
        var ex = new BookingUnavailableException("Não há funcionário disponível...", suggested);

        var problem = handler.handleBookingUnavailable(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getProperties()).containsEntry("suggestedSlot", "10/08/2026 09:30");
    }

    @Test
    void bookingUnavailableWithoutSuggestionHasNullSuggestedSlot() {
        var ex = new BookingUnavailableException("Não há mais horários disponíveis...", null);

        var problem = handler.handleBookingUnavailable(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getProperties()).containsEntry("suggestedSlot", null);
    }
}
