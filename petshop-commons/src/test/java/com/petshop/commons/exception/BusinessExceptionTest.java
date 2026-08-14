package com.petshop.commons.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    // Subclasse local só pra exercitar o construtor (message, cause), que nenhuma subclasse real usa.
    private static class MessageOnlyException extends BusinessException {
        MessageOnlyException(String message) {
            super(message);
        }
    }

    private static class MessageAndCauseException extends BusinessException {
        MessageAndCauseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Test
    void messageOnlyConstructorSetsMessage() {
        var ex = new MessageOnlyException("algo deu errado");

        assertThat(ex.getMessage()).isEqualTo("algo deu errado");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void messageAndCauseConstructorSetsBoth() {
        var cause = new IllegalStateException("causa raiz");
        var ex = new MessageAndCauseException("algo deu errado", cause);

        assertThat(ex.getMessage()).isEqualTo("algo deu errado");
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void isARuntimeException() {
        var ex = new MessageOnlyException("qualquer coisa");

        assertThat(ex).isInstanceOf(RuntimeException.class);
    }
}
