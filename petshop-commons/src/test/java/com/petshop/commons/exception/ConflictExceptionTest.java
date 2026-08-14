package com.petshop.commons.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConflictExceptionTest {

    @Test
    void setsMessage() {
        var ex = new ConflictException("recurso já existe");

        assertThat(ex.getMessage()).isEqualTo("recurso já existe");
    }

    @Test
    void isABusinessException() {
        var ex = new ConflictException("recurso já existe");

        assertThat(ex).isInstanceOf(BusinessException.class);
    }
}
