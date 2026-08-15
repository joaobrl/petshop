package com.petshop.staff_service.api.rest.handler;

import com.petshop.commons.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class StaffExceptionHandlerTest {

    @Test
    void inheritsBaseExceptionHandlerBehavior() {
        var handler = new StaffExceptionHandler();

        var problem = handler.handleNotFound(new NotFoundException("Funcionário", "x"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
    }
}
