package com.petshop.commons.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Responde 403 com corpo ProblemDetail (RFC 9457) quando um usuário autenticado
 * não tem o papel exigido pela rota (SDD-034).
 *
 * <p>Escreve direto na response, sem {@code sendError}: com {@code sendError(403)}
 * o container redespacha para {@code /error}, onde a autenticação Bearer não
 * roda de novo, a requisição chega anônima e o entry point responde 401 — o que
 * desloga o usuário no frontend. Escrevendo direto, {@code /error} não precisa
 * virar rota aberta.
 *
 * <p>Não implementa {@code AccessDeniedHandler} (spring-security-web não é
 * dependência deste módulo): o serviço o registra com a referência de método
 * {@code handler::handle}, que casa com a interface funcional.
 */
public class ProblemDetailAccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);

    static final String TITLE = "Acesso negado";
    static final String DETAIL = "Você não tem permissão para acessar este recurso.";

    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Access denied: {} {}", request.getMethod(), request.getRequestURI());

        String body = "{\"type\":\"about:blank\""
                + ",\"title\":\"" + TITLE + "\""
                + ",\"status\":" + HttpStatus.FORBIDDEN.value()
                + ",\"detail\":\"" + DETAIL + "\""
                + ",\"instance\":\"" + escape(request.getRequestURI()) + "\"}";

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
