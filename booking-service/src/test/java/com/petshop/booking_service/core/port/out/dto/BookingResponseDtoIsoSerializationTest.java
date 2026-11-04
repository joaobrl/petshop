package com.petshop.booking_service.core.port.out.dto;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class BookingResponseDtoIsoSerializationTest {

    @Autowired
    private JsonMapper springMvcMapper;

    private BookingResponseDto dto;

    @BeforeEach
    void setUp() {
        var request = new BookingRequestDto();
        request.setPetId(UUID.randomUUID().toString());
        request.setServiceType(ServiceType.BANHO);
        request.setBookingDateTime(LocalDateTime.of(2026, 8, 15, 9, 0));
        var booking = new Booking(request);
        booking.setId(UUID.randomUUID());
        dto = new BookingResponseDto(booking);
    }

    @Test
    @DisplayName("Deve serializar bookingDateTime em ISO-8601 pelo ObjectMapper do Spring MVC")
    void shouldSerializeBookingDateTimeAsIsoWhenUsingSpringMvcMapper() {
        var json = springMvcMapper.readTree(springMvcMapper.writeValueAsString(dto));

        assertThat(json.get("bookingDateTime").asString()).isEqualTo("2026-08-15T09:00:00");
    }

    @Test
    @DisplayName("Deve serializar bookingDateTime em ISO-8601 pelo JsonSerializer do spring-kafka")
    void shouldSerializeBookingDateTimeAsIsoWhenUsingKafkaJsonSerializer() throws Exception {
        try (var serializer = new JsonSerializer<BookingResponseDto>()) {
            var payload = new String(serializer.serialize("booking-completed", dto), StandardCharsets.UTF_8);

            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);

            assertThat(json.get("bookingDateTime").isTextual()).isTrue();
            assertThat(json.get("bookingDateTime").asText()).isEqualTo("2026-08-15T09:00:00");
        }
    }
}
