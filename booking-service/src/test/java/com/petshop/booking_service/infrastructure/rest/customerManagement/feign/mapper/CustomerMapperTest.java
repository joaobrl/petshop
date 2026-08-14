package com.petshop.booking_service.infrastructure.rest.customerManagement.feign.mapper;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerMapperTest {

    private final CustomerMapper mapper = new CustomerMapper();

    @Test
    void formatsDateTimeToQueryParamPattern() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 30);

        assertThat(mapper.toQueryParam(dateTime)).isEqualTo("15/08/2026 09:30");
    }

    @Test
    void returnsNullForNullDateTime() {
        assertThat(mapper.toQueryParam(null)).isNull();
    }
}
