package com.petshop.commons.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessRuleExceptionTest {

    @Test
    void setsMessage() {
        var ex = new BusinessRuleException("violação de regra de negócio");

        assertThat(ex.getMessage()).isEqualTo("violação de regra de negócio");
    }

    @Test
    void isABusinessException() {
        var ex = new BusinessRuleException("violação de regra de negócio");

        assertThat(ex).isInstanceOf(BusinessException.class);
    }
}
