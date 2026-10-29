package com.petshop.notification_service.core.application.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailMaskerTest {

    @Test
    void keepsOnlyTheFirstTwoCharactersOfTheLocalPart() {
        assertThat(EmailMasker.mask("cliente@petshop.com")).isEqualTo("cl***@petshop.com");
    }

    @Test
    void handlesShortLocalParts() {
        assertThat(EmailMasker.mask("a@petshop.com")).isEqualTo("a***@petshop.com");
    }

    @Test
    void masksEverythingWhenThereIsNoValidAddress() {
        assertThat(EmailMasker.mask(null)).isEqualTo("***");
        assertThat(EmailMasker.mask(" ")).isEqualTo("***");
        assertThat(EmailMasker.mask("sem-arroba")).isEqualTo("***");
    }
}
