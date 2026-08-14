package com.petshop.booking_service.api.rest.handler;

import com.petshop.booking_service.core.domain.exception.BookingUnavailableException;
import com.petshop.commons.handler.BaseExceptionHandler;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.format.DateTimeFormatter;

@RestControllerAdvice
public class BookingExceptionHandler extends BaseExceptionHandler {

    private static final DateTimeFormatter SLOT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @ExceptionHandler(BookingUnavailableException.class)
    public ProblemDetail handleBookingUnavailable(BookingUnavailableException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Business rule violation");
        problem.setProperty("suggestedSlot",
                ex.getSuggestedSlot() != null ? ex.getSuggestedSlot().format(SLOT_FORMATTER) : null);
        return problem;
    }

}
