package com.petshop.commons.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponseDto(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldErrorDto> fieldErrors
) {
    public record FieldErrorDto(String field, String message) {}
}