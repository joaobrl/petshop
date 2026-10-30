package com.petshop.commons.handler;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Map;

public abstract class BaseExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(BaseExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Resource conflict");
        return problem;
    }

    // Cobre também DuplicateKeyException (subclasse, usada pelo Spring Data Mongo em índice único)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", maskValues(ex.getMostSpecificCause().getMessage()));
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Já existe um registro com esses dados.");
        problem.setTitle("Resource conflict");
        return problem;
    }

    // RNF3 (LGPD): a mensagem do banco traz o valor duplicado (CPF/e-mail); o log
    // mantém coluna/constraint e mascara os valores.
    //   Postgres: Key (cpf)=(123) already exists  ->  Key (cpf)=(***) already exists
    //   Mongo:    dup key: { cpf: "123" }         ->  dup key: { *** }
    //   Postgres (not-null/check): Failing row contains (1, 123, ...)  ->  Failing row contains (***)
    static String maskValues(String message) {
        if (message == null) {
            return null;
        }
        return message
                .replaceAll("(Key \\([^)]*\\)=)\\([^)]*\\)", "$1(***)")
                .replaceAll("dup key: \\{[^}]*}", "dup key: { *** }")
                // lc_messages pt_BR do Postgres traduz o texto ("A linha que falhou contém")
                .replaceAll("(Failing row contains|A linha que falhou contém) \\(.*\\)", "$1 (***)");
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        log.warn("Business rule violation: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Business rule violation");
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Falha na validação");
        problem.setTitle("Invalid request");

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "inválido"
                ))
                .toList();
        problem.setProperty("errors", errors);

        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Corpo da requisição malformado: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Corpo da requisição malformado ou com um campo em formato inválido.");
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParameter(MissingServletRequestParameterException ex) {
        log.warn("Parâmetro obrigatório ausente: {}", ex.getParameterName());
        return badRequest("Parâmetro obrigatório ausente: " + ex.getParameterName(), ex.getParameterName());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        log.warn("Header obrigatório ausente: {}", ex.getHeaderName());
        return badRequest("Header obrigatório ausente: " + ex.getHeaderName(), ex.getHeaderName());
    }

    // Não ecoa o valor recebido na resposta (evita refletir input do usuário).
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Valor inválido para o parâmetro '{}'", ex.getName());
        return badRequest("Valor inválido para o parâmetro '" + ex.getName() + "'", ex.getName());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException ex) {
        log.warn("Parte obrigatória ausente: {}", ex.getRequestPartName());
        return badRequest("Parte obrigatória ausente: " + ex.getRequestPartName(), ex.getRequestPartName());
    }

    // Erro de mapeamento do servidor, não do cliente: continua 500. Sem este
    // handler cairia no de ServletRequestBindingException (superclasse) e viraria 400.
    @ExceptionHandler(MissingPathVariableException.class)
    public ProblemDetail handleMissingPathVariable(MissingPathVariableException ex) {
        return handleGeneric(ex);
    }

    // Demais erros de binding (cookie ausente, params conditions etc.)
    @ExceptionHandler(ServletRequestBindingException.class)
    public ProblemDetail handleRequestBinding(ServletRequestBindingException ex) {
        log.warn("Requisição inválida: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Requisição inválida.");
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.warn("Método HTTP não suportado: {}", ex.getMethod());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED,
                "Método HTTP não suportado: " + ex.getMethod());
        problem.setTitle("Method not allowed");
        var response = ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED);
        // RFC 9110: 405 deve informar os métodos aceitos no header Allow
        if (ex.getSupportedHttpMethods() != null) {
            response.allow(ex.getSupportedHttpMethods().toArray(HttpMethod[]::new));
        }
        return response.body(problem);
    }

    // Não ecoa o Content-Type/Accept recebido (input do usuário).
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("Content-Type não suportado: {}", ex.getContentType());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type não suportado. Tipos aceitos: " + MediaType.toString(ex.getSupportedMediaTypes()));
        problem.setTitle("Unsupported media type");
        return problem;
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ProblemDetail handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        log.warn("Accept não atendível: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_ACCEPTABLE,
                "Nenhuma representação disponível para o Accept informado. Tipos disponíveis: "
                        + MediaType.toString(ex.getSupportedMediaTypes()));
        problem.setTitle("Not acceptable");
        return problem;
    }

    // Rota inexistente. Não ecoa o path recebido na resposta.
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ProblemDetail handleNoRoute(Exception ex) {
        log.warn("Rota não encontrada: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Recurso não encontrado.");
        problem.setTitle("Resource not found");
        return problem;
    }

    private static ProblemDetail badRequest(String detail, String parameter) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Invalid request");
        problem.setProperty("parameter", parameter);
        return problem;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setTitle("Access denied");
        return problem;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        log.warn("Bad credentials: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setTitle("Invalid credentials");
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("Upload excede o tamanho máximo permitido: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "Arquivo enviado excede o tamanho máximo permitido.");
        problem.setTitle("Payload too large");
        return problem;
    }

    // Lock otimista (SDD-010): outra requisição gravou o mesmo registro entre
    // a leitura e a escrita. Captura a superclasse (spring-tx, já no commons)
    // em vez de ObjectOptimisticLockingFailureException (spring-orm), que não
    // está no classpath dos serviços Mongo; a Object... é subclasse, então
    // continua coberta. O cliente decide se tenta de novo.
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Conflito de versão (lock otimista): {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "O registro foi alterado por outra requisição. Recarregue e tente novamente.");
        problem.setTitle("Resource conflict");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro inesperado. Tente novamente mais tarde."
        );
        problem.setTitle("Internal server error");
        return problem;
    }
}