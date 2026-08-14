package com.petshop.order_service.infrastructure.messaging.dto;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SendEmailCommandDtoTest {

    @Test
    void exposesAllFieldsViaAccessors() {
        var params = Map.<String, Object>of("customerName", "Maria");
        var dto = new SendEmailCommandDto("maria@mail.com", "PEDIDO_AGUARDANDO_PAGAMENTO", params);

        assertThat(dto.to()).isEqualTo("maria@mail.com");
        assertThat(dto.tipo()).isEqualTo("PEDIDO_AGUARDANDO_PAGAMENTO");
        assertThat(dto.params()).isEqualTo(params);
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var params = Map.<String, Object>of("customerName", "Maria");
        var a = new SendEmailCommandDto("maria@mail.com", "PEDIDO_AGUARDANDO_PAGAMENTO", params);
        var b = new SendEmailCommandDto("maria@mail.com", "PEDIDO_AGUARDANDO_PAGAMENTO", params);
        var c = new SendEmailCommandDto("outro@mail.com", "PEDIDO_AGUARDANDO_PAGAMENTO", params);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("PEDIDO_AGUARDANDO_PAGAMENTO");
    }
}
