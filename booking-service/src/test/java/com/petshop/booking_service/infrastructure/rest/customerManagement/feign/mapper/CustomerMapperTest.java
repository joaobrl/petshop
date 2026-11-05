package com.petshop.booking_service.infrastructure.rest.customerManagement.feign.mapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerMapperTest {

    private final CustomerMapper mapper = new CustomerMapper();

    @Test
    void formatsDateTimeToIsoLocalDateTime() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 30);

        assertThat(mapper.toQueryParam(dateTime)).isEqualTo("2026-08-15T09:30:00");
    }

    @Test
    void alwaysIncludesSecondsEvenWhenZero() {
        assertThat(mapper.toQueryParam(LocalDateTime.of(2026, 8, 15, 9, 0))).isEqualTo("2026-08-15T09:00:00");
    }

    @Test
    void returnsNullForNullDateTime() {
        assertThat(mapper.toQueryParam(null)).isNull();
    }
}
