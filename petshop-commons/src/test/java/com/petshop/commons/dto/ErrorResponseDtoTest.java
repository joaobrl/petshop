package com.petshop.commons.dto;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorResponseDtoTest {

    @Test
    void exposesAllFieldsViaAccessors() {
        var timestamp = LocalDateTime.now();
        var fieldErrors = List.of(new ErrorResponseDto.FieldErrorDto("email", "obrigatório"));

        var dto = new ErrorResponseDto(timestamp, 400, "Bad Request", "Invalid request", "/api/v1/x", fieldErrors);

        assertThat(dto.timestamp()).isEqualTo(timestamp);
        assertThat(dto.status()).isEqualTo(400);
        assertThat(dto.error()).isEqualTo("Bad Request");
        assertThat(dto.message()).isEqualTo("Invalid request");
        assertThat(dto.path()).isEqualTo("/api/v1/x");
        assertThat(dto.fieldErrors()).isEqualTo(fieldErrors);
    }

    @Test
    void fieldErrorDtoExposesFieldAndMessage() {
        var fieldError = new ErrorResponseDto.FieldErrorDto("cpf", "inválido");

        assertThat(fieldError.field()).isEqualTo("cpf");
        assertThat(fieldError.message()).isEqualTo("inválido");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var timestamp = LocalDateTime.now();
        var fieldErrors = List.of(new ErrorResponseDto.FieldErrorDto("email", "obrigatório"));

        var a = new ErrorResponseDto(timestamp, 400, "Bad Request", "Invalid request", "/api/v1/x", fieldErrors);
        var b = new ErrorResponseDto(timestamp, 400, "Bad Request", "Invalid request", "/api/v1/x", fieldErrors);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("status");
    }

    @Test
    void fieldErrorDtoEqualsAndHashCode() {
        var a = new ErrorResponseDto.FieldErrorDto("cpf", "inválido");
        var b = new ErrorResponseDto.FieldErrorDto("cpf", "inválido");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("cpf");
    }
}
