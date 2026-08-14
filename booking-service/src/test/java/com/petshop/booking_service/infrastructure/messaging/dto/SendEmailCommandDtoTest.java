package com.petshop.booking_service.infrastructure.messaging.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SendEmailCommandDtoTest {

    @Test
    void exposesFieldsViaAccessors() {
        var params = Map.<String, Object>of("ownerName", "Ciclana");
        var command = new SendEmailCommandDto("cliente@petshop.com", "AGENDAMENTO_CONFIRMADO", params);

        assertThat(command.to()).isEqualTo("cliente@petshop.com");
        assertThat(command.tipo()).isEqualTo("AGENDAMENTO_CONFIRMADO");
        assertThat(command.params()).isEqualTo(params);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var params = Map.<String, Object>of("ownerName", "Ciclana");
        var a = new SendEmailCommandDto("cliente@petshop.com", "AGENDAMENTO_CONFIRMADO", params);
        var b = new SendEmailCommandDto("cliente@petshop.com", "AGENDAMENTO_CONFIRMADO", params);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
