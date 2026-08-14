package com.petshop.commons.handler;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BaseExceptionHandlerTest {

    private static class TestExceptionHandler extends BaseExceptionHandler {
    }

    private final TestExceptionHandler handler = new TestExceptionHandler();

    @Test
    void handlesNotFound() {
        var ex = new NotFoundException("Product", 1L);

        var problem = handler.handleNotFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).isEqualTo("Product not found with identifier: 1");
    }

    @Test
    void handlesConflict() {
        var ex = new ConflictException("já existe");

        var problem = handler.handleConflict(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Resource conflict");
        assertThat(problem.getDetail()).isEqualTo("já existe");
    }

    @Test
    void handlesBusinessRule() {
        var ex = new BusinessRuleException("regra violada");

        var problem = handler.handleBusinessRule(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getTitle()).isEqualTo("Business rule violation");
        assertThat(problem.getDetail()).isEqualTo("regra violada");
    }

    @Test
    void handlesValidationWithFieldErrorsIncludingFallbackMessage() throws NoSuchMethodException {
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "email", "obrigatório"));
        bindingResult.addError(new FieldError("target", "cpf", null));

        MethodParameter methodParameter = new MethodParameter(
                BaseExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        var ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        var problem = handler.handleValidation(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid request");
        assertThat(problem.getDetail()).isEqualTo("Validation failed");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) problem.getProperties().get("errors");
        assertThat(errors).hasSize(2);
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.get("field")).isEqualTo("email");
            assertThat(e.get("message")).isEqualTo("obrigatório");
        });
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.get("field")).isEqualTo("cpf");
            assertThat(e.get("message")).isEqualTo("invalid");
        });
    }

    @SuppressWarnings("unused")
    private void dummyMethod(String param) {
    }

    @Test
    void handlesAccessDenied() {
        var ex = new AccessDeniedException("sem permissão");

        var problem = handler.handleAccessDenied(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("Access denied");
        assertThat(problem.getDetail()).isEqualTo("sem permissão");
    }

    @Test
    void handlesBadCredentials() {
        var ex = new BadCredentialsException("credenciais inválidas");

        var problem = handler.handleBadCredentials(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid credentials");
        assertThat(problem.getDetail()).isEqualTo("credenciais inválidas");
    }

    @Test
    void handlesIllegalArgument() {
        var ex = new IllegalArgumentException("argumento inválido");

        var problem = handler.handleIllegalArgument(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid request");
        assertThat(problem.getDetail()).isEqualTo("argumento inválido");
    }

    @Test
    void handlesGenericExceptionWithoutLeakingDetails() {
        var ex = new RuntimeException("detalhe interno sensível, stack trace, etc.");

        var problem = handler.handleGeneric(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Internal server error");
        assertThat(problem.getDetail())
                .isEqualTo("An unexpected error occurred. Please try again later.")
                .doesNotContain("detalhe interno sensível");
    }
}
