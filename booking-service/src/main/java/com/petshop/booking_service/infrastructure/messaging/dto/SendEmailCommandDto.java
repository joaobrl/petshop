package com.petshop.booking_service.infrastructure.messaging.dto;

import java.util.Map;

public record SendEmailCommandDto(
        String to,
        String tipo,
        Map<String, Object> params
) {}
