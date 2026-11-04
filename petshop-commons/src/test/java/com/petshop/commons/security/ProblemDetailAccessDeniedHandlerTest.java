package com.petshop.commons.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemDetailAccessDeniedHandlerTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private StringWriter body;
    private ProblemDetailAccessDeniedHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        body = new StringWriter();
        handler = new ProblemDetailAccessDeniedHandler();
        when(response.getWriter()).thenReturn(new PrintWriter(body, true));
        when(request.getMethod()).thenReturn("GET");
    }

    @Test
    @DisplayName("Deve responder 403 com content-type problem+json em UTF-8")
    void shouldRespond403WithProblemJsonWhenAccessDenied() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/weekend-allocation");

        handler.handle(request, response, new AccessDeniedException("negado"));

        verify(response).setStatus(403);
        verify(response).setContentType("application/problem+json");
        verify(response).setCharacterEncoding("UTF-8");
    }

    @Test
    @DisplayName("Deve escrever corpo com title, detail, status e instance da requisição")
    void shouldWriteProblemBodyWithTitleDetailAndInstance() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/v1/weekend-allocation");

        handler.handle(request, response, new AccessDeniedException("negado"));

        assertThat(body.toString())
                .contains("\"type\":\"about:blank\"")
                .contains("\"title\":\"Acesso negado\"")
                .contains("\"status\":403")
                .contains("\"detail\":\"Você não tem permissão para acessar este recurso.\"")
                .contains("\"instance\":\"/api/v1/weekend-allocation\"");
    }

    @Test
    @DisplayName("Deve escapar aspas e barras invertidas no instance")
    void shouldEscapeQuotesAndBackslashesInInstance() throws Exception {
        when(request.getRequestURI()).thenReturn("/a\"b\\c");

        handler.handle(request, response, new AccessDeniedException("negado"));

        assertThat(body.toString()).contains("\"instance\":\"/a\\\"b\\\\c\"");
    }

    @Test
    @DisplayName("Deve usar instance vazio quando a URI da requisição for nula")
    void shouldUseEmptyInstanceWhenRequestUriIsNull() throws Exception {
        when(request.getRequestURI()).thenReturn(null);

        handler.handle(request, response, new AccessDeniedException("negado"));

        assertThat(body.toString()).contains("\"instance\":\"\"");
    }
}
