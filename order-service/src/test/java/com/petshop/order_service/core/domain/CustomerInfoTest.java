package com.petshop.order_service.core.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerInfoTest {

    @Test
    void exposesAllFieldsViaAccessors() {
        var id = UUID.randomUUID();
        var info = new CustomerInfo(id, "Maria", "maria@mail.com");

        assertThat(info.id()).isEqualTo(id);
        assertThat(info.name()).isEqualTo("Maria");
        assertThat(info.email()).isEqualTo("maria@mail.com");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var id = UUID.randomUUID();
        var a = new CustomerInfo(id, "Maria", "maria@mail.com");
        var b = new CustomerInfo(id, "Maria", "maria@mail.com");
        var c = new CustomerInfo(UUID.randomUUID(), "Maria", "maria@mail.com");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
        assertThat(a.toString()).contains("Maria");
    }
}
