package com.petshop.commons.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotFoundExceptionTest {

    @Test
    void messageOnlyConstructorSetsMessageVerbatim() {
        var ex = new NotFoundException("não encontrado");

        assertThat(ex.getMessage()).isEqualTo("não encontrado");
    }

    @Test
    void resourceAndIdentifierConstructorBuildsMessage() {
        var ex = new NotFoundException("Product", 42L);

        assertThat(ex.getMessage()).isEqualTo("Product not found with identifier: 42");
    }

    @Test
    void isABusinessException() {
        var ex = new NotFoundException("Product", 42L);

        assertThat(ex).isInstanceOf(BusinessException.class);
    }
}
