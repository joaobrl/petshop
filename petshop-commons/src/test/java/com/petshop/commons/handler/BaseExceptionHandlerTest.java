package com.petshop.commons.handler;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.NotFoundException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BaseExceptionHandlerTest {

    private static class TestExceptionHandler extends BaseExceptionHandler {
    }

    private final TestExceptionHandler handler = new TestExceptionHandler();

    @Test
    void handlesNotFound() {
        var ex = new NotFoundException("Produto", 1L);

        var problem = handler.handleNotFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).isEqualTo("Produto não encontrado(a) com identificador: 1");
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
        assertThat(problem.getDetail()).isEqualTo("Falha na validação");

        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) problem.getProperties().get("errors");
        assertThat(errors).hasSize(2);
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.get("field")).isEqualTo("email");
            assertThat(e.get("message")).isEqualTo("obrigatório");
        });
        assertThat(errors).anySatisfy(e -> {
            assertThat(e.get("field")).isEqualTo("cpf");
            assertThat(e.get("message")).isEqualTo("inválido");
        });
    }

    @SuppressWarnings("unused")
    private void dummyMethod(String param) {
    }

    @Test
    void handlesMessageNotReadable() {
        var ex = new HttpMessageNotReadableException("JSON parse error: Cannot deserialize value of type `java.time.LocalDateTime`");

        var problem = handler.handleMessageNotReadable(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid request");
        assertThat(problem.getDetail())
                .isEqualTo("Corpo da requisição malformado ou com um campo em formato inválido.")
                .doesNotContain("LocalDateTime");
    }

    @Test
    void handlesMissingParameterAsBadRequest() {
        var ex = new MissingServletRequestParameterException("customerId", "UUID");

        var problem = handler.handleMissingParameter(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid request");
        assertThat(problem.getDetail()).isEqualTo("Parâmetro obrigatório ausente: customerId");
        assertThat(problem.getProperties()).containsEntry("parameter", "customerId");
    }

    @Test
    void handlesMissingHeaderAsBadRequest() throws NoSuchMethodException {
        var param = new MethodParameter(
                BaseExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        var ex = new MissingRequestHeaderException("Authorization", param);

        var problem = handler.handleMissingHeader(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid request");
        assertThat(problem.getDetail()).isEqualTo("Header obrigatório ausente: Authorization");
    }

    @Test
    void handlesTypeMismatchWithoutEchoingValue() throws NoSuchMethodException {
        var param = new MethodParameter(
                BaseExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        var ex = new MethodArgumentTypeMismatchException("abc", UUID.class, "customerId", param, null);

        var problem = handler.handleTypeMismatch(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid request");
        assertThat(problem.getDetail())
                .isEqualTo("Valor inválido para o parâmetro 'customerId'")
                .doesNotContain("abc");
    }

    @Test
    void handlesMissingPartAsBadRequest() {
        var ex = new MissingServletRequestPartException("file");

        var problem = handler.handleMissingPart(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid request");
        assertThat(problem.getDetail()).isEqualTo("Parte obrigatória ausente: file");
        assertThat(problem.getProperties()).containsEntry("parameter", "file");
    }

    @Test
    void keepsMissingPathVariableAsInternalServerError() throws NoSuchMethodException {
        var param = new MethodParameter(
                BaseExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class), 0);
        var ex = new MissingPathVariableException("id", param);

        var problem = handler.handleMissingPathVariable(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    @Test
    void handlesOtherRequestBindingErrorsAsBadRequest() {
        var ex = new ServletRequestBindingException("Missing cookie 'session'");

        var problem = handler.handleRequestBinding(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid request");
        assertThat(problem.getDetail()).isEqualTo("Requisição inválida.");
    }

    @Test
    void handlesMethodNotSupportedWithAllowHeader() {
        var ex = new HttpRequestMethodNotSupportedException("PUT", List.of("GET", "POST"));

        var response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.POST);
        assertThat(response.getBody().getTitle()).isEqualTo("Method not allowed");
        assertThat(response.getBody().getDetail()).isEqualTo("Método HTTP não suportado: PUT");
    }

    @Test
    void handlesMethodNotSupportedWithoutKnownMethods() {
        var ex = new HttpRequestMethodNotSupportedException("PUT");

        var response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow()).isEmpty();
    }

    @Test
    void handlesUnsupportedMediaTypeWithoutEchoingContentType() {
        var ex = new HttpMediaTypeNotSupportedException(
                MediaType.parseMediaType("text/x-evil"), List.of(MediaType.APPLICATION_JSON));

        var problem = handler.handleMediaTypeNotSupported(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        assertThat(problem.getTitle()).isEqualTo("Unsupported media type");
        assertThat(problem.getDetail())
                .contains("application/json")
                .doesNotContain("text/x-evil");
    }

    @Test
    void handlesNotAcceptable() {
        var ex = new HttpMediaTypeNotAcceptableException(List.of(MediaType.APPLICATION_JSON));

        var problem = handler.handleMediaTypeNotAcceptable(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_ACCEPTABLE.value());
        assertThat(problem.getTitle()).isEqualTo("Not acceptable");
        assertThat(problem.getDetail()).contains("application/json");
    }

    @Test
    void handlesNoResourceFoundWithoutEchoingPath() {
        var ex = new NoResourceFoundException(HttpMethod.GET, "api/v1/<script>");

        var problem = handler.handleNoRoute(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail())
                .isEqualTo("Recurso não encontrado.")
                .doesNotContain("<script>");
    }

    @Test
    void handlesNoHandlerFound() {
        var ex = new NoHandlerFoundException("GET", "/api/v1/nada", HttpHeaders.EMPTY);

        var problem = handler.handleNoRoute(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
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
    void handlesDataIntegrityViolationAsConflict() {
        var ex = new DataIntegrityViolationException("violação de constraint");

        var problem = handler.handleDataIntegrity(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Resource conflict");
        assertThat(problem.getDetail()).isEqualTo("Já existe um registro com esses dados.");
    }

    @Test
    void dataIntegrityDetailDoesNotLeakDatabaseDetails() {
        var ex = new DataIntegrityViolationException(
                "could not execute statement [ERROR: duplicate key value violates unique constraint "
                        + "\"uk_customer_cpf\" Detail: Key (cpf)=(123.456.789-00) already exists.] "
                        + "SQL: insert into customer (cpf, email) values (?, ?)");

        var problem = handler.handleDataIntegrity(ex);

        assertThat(problem.getDetail())
                .doesNotContain("cpf")
                .doesNotContain("email")
                .doesNotContain("constraint")
                .doesNotContain("uk_customer")
                .doesNotContain("123.456.789-00")
                .doesNotContain("insert")
                .doesNotContain("SQL");
        assertThat(problem.getProperties()).isNullOrEmpty();
    }

    @Test
    void handlesDuplicateKeyAsConflict() {
        var ex = new DuplicateKeyException("E11000 duplicate key error collection: products index: sku_1");

        var problem = handler.handleDataIntegrity(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Resource conflict");
        assertThat(problem.getDetail())
                .isEqualTo("Já existe um registro com esses dados.")
                .doesNotContain("sku_1");
    }

    @Test
    void handlesGenericExceptionWithoutLeakingDetails() {
        var ex = new RuntimeException("detalhe interno sensível, stack trace, etc.");

        var problem = handler.handleGeneric(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Internal server error");
        assertThat(problem.getDetail())
                .isEqualTo("Ocorreu um erro inesperado. Tente novamente mais tarde.")
                .doesNotContain("detalhe interno sensível");
    }

    private List<String> captureWarnLogs(Runnable action) {
        var logger = (Logger) LoggerFactory.getLogger(BaseExceptionHandler.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    void logMasksPostgresValuesButKeepsColumn() {
        var cause = new RuntimeException("ERROR: duplicate key value violates unique constraint \"uk_cpf\"\n"
                + "  Detail: Key (cpf)=(12345678900) already exists.");

        var logs = captureWarnLogs(() ->
                handler.handleDataIntegrity(new DataIntegrityViolationException("x", cause)));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0)).contains("uk_cpf").contains("Key (cpf)=(***)")
                .doesNotContain("12345678900");
    }

    @Test
    void logMasksMongoValuesAndKeepsIndexName() {
        var cause = new RuntimeException("E11000 duplicate key error collection: db.customers index: cpf_1 "
                + "dup key: { cpf: \"12345678900\" }");

        var logs = captureWarnLogs(() ->
                handler.handleDataIntegrity(new DuplicateKeyException("x", cause)));

        assertThat(logs).hasSize(1);
        assertThat(logs.get(0)).contains("cpf_1").contains("dup key: { *** }")
                .doesNotContain("12345678900");
    }

    @Test
    void maskValuesHandlesNullAndMessagesWithoutValues() {
        assertThat(BaseExceptionHandler.maskValues(null)).isNull();
        assertThat(BaseExceptionHandler.maskValues("violates foreign key constraint fk_x"))
                .isEqualTo("violates foreign key constraint fk_x");
    }

    @Test
    void maskValuesMasksCompositeKeys() {
        assertThat(BaseExceptionHandler.maskValues("Key (cpf, email)=(123, a@b.com) already exists."))
                .isEqualTo("Key (cpf, email)=(***) already exists.");
    }

    @Test
    void maskValuesMasksFailingRowOfNotNullViolation() {
        assertThat(BaseExceptionHandler.maskValues(
                "null value in column \"phone\" violates not-null constraint\n  Detail: Failing row contains (7, 12345678900, a@b.com, null)."))
                .isEqualTo("null value in column \"phone\" violates not-null constraint\n  Detail: Failing row contains (***).");
        assertThat(BaseExceptionHandler.maskValues("Detalhe: A linha que falhou contém (7, 12345678900, null)."))
                .isEqualTo("Detalhe: A linha que falhou contém (***).");
    }

    @Test
    void handlerDoesNotFailWhenCauseMessageIsNull() {
        var problem = handler.handleDataIntegrity(new DataIntegrityViolationException(null));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }
}
