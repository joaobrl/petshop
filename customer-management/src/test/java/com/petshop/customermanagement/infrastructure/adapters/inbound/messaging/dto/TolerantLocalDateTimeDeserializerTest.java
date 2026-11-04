package com.petshop.customermanagement.infrastructure.adapters.inbound.messaging.dto;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.support.JacksonUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TolerantLocalDateTimeDeserializerTest {

    private final ObjectMapper mapper = JacksonUtils.enhancedObjectMapper();

    private BookingEventDTO read(String bookingDateTimeJson) throws Exception {
        return mapper.readValue("{\"bookingDateTime\":" + bookingDateTimeJson + "}", BookingEventDTO.class);
    }

    @ParameterizedTest
    @CsvSource({
            "2026-08-15T09:00:00, 2026-08-15T09:00:00",
            "2026-08-15T09:00, 2026-08-15T09:00:00",
            "2026-08-15T09:30:45, 2026-08-15T09:30:45",
            "15/08/2026 09:00, 2026-08-15T09:00:00",
            "  15/08/2026 09:00  , 2026-08-15T09:00:00"
    })
    @DisplayName("Deve desserializar string ISO-8601 ou formato legado para LocalDateTime")
    void shouldParseStringWhenIsoOrLegacyFormat(String input, LocalDateTime expected) throws Exception {
        var event = read("\"" + input + "\"");

        assertThat(event.bookingDateTime()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Deve desserializar array numérico pelo desserializador padrão")
    void shouldParseArrayWhenValueIsNotString() throws Exception {
        var event = read("[2026,8,15,9,0]");

        assertThat(event.bookingDateTime()).isEqualTo(LocalDateTime.of(2026, 8, 15, 9, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"   \""})
    @DisplayName("Deve retornar nulo quando a string estiver vazia ou em branco")
    void shouldReturnNullWhenStringIsBlank(String json) throws Exception {
        var event = read(json);

        assertThat(event.bookingDateTime()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"nao-e-data", "2026-08-15", "15/08/2026", "32/13/2026 25:00"})
    @DisplayName("Deve lançar erro de mapeamento quando a string não for ISO nem legada")
    void shouldThrowMappingErrorWhenStringIsInvalid(String input) {
        assertThatThrownBy(() -> read("\"" + input + "\""))
                .isInstanceOf(JsonMappingException.class)
                .hasMessageContaining("Esperado ISO-8601");
    }
}
