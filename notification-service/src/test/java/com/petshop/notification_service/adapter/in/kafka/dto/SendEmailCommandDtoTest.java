package com.petshop.notification_service.adapter.in.kafka.dto;

import com.petshop.notification_service.core.domain.TipoNotificacao;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SendEmailCommandDtoTest {

    @Test
    void isAnImmutableRecordWithWorkingAccessors() {
        var params = Map.<String, Object>of("ownerName", "Ciclana");
        var command = new SendEmailCommandDto("cliente@petshop.com", TipoNotificacao.AGENDAMENTO_CONFIRMADO, params);

        assertThat(command.to()).isEqualTo("cliente@petshop.com");
        assertThat(command.tipo()).isEqualTo(TipoNotificacao.AGENDAMENTO_CONFIRMADO);
        assertThat(command.params()).isEqualTo(params);
    }

    @Test
    void recordEqualityIsFieldBased() {
        var params = Map.<String, Object>of("ownerName", "Ciclana");
        var first = new SendEmailCommandDto("cliente@petshop.com", TipoNotificacao.AGENDAMENTO_CONFIRMADO, params);
        var second = new SendEmailCommandDto("cliente@petshop.com", TipoNotificacao.AGENDAMENTO_CONFIRMADO, params);

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }
}
