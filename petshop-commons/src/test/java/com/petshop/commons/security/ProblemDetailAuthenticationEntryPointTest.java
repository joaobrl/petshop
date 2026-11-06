package com.petshop.commons.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class ProblemDetailAuthenticationEntryPointTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private StringWriter body;
    private ProblemDetailAuthenticationEntryPoint entryPoint;

    @BeforeEach
    void setUp() throws Exception {
        body = new StringWriter();
        entryPoint = new ProblemDetailAuthenticationEntryPoint();
        given(response.getWriter()).willReturn(new PrintWriter(body, true));
    }

    @Test
    @DisplayName("Deve responder 401 com WWW-Authenticate Bearer e corpo ProblemDetail quando não houver token")
    void shouldRespond401WithBearerChallengeAndProblemBodyWhenNoToken() throws Exception {
        given(request.getRequestURI()).willReturn("/api/v1/orders/1");

        entryPoint.commence(request, response, new InsufficientAuthenticationException("sem token"));

        then(response).should().setStatus(401);
        then(response).should().setHeader("WWW-Authenticate", "Bearer");
        then(response).should().setContentType("application/problem+json");
        then(response).should().setCharacterEncoding("UTF-8");
        assertThat(body.toString())
                .contains("\"type\":\"about:blank\"")
                .contains("\"title\":\"Não autenticado\"")
                .contains("\"status\":401")
                .contains("\"detail\":\"Token de acesso ausente, inválido ou expirado.\"")
                .contains("\"instance\":\"/api/v1/orders/1\"");
    }

    @Test
    @DisplayName("Deve informar error invalid_token no WWW-Authenticate quando a exceção for OAuth2AuthenticationException")
    void shouldAnnounceInvalidTokenWhenOAuth2AuthenticationException() throws Exception {
        given(request.getRequestURI()).willReturn("/api/v1/orders/1");

        entryPoint.commence(request, response, new OAuth2AuthenticationException(new OAuth2Error("invalid_token")));

        then(response).should().setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        then(response).should().setStatus(401);
    }

    @Test
    @DisplayName("Deve informar error invalid_token no WWW-Authenticate quando o token bearer for inválido")
    void shouldAnnounceInvalidTokenWhenBearerTokenIsInvalid() throws Exception {
        given(request.getRequestURI()).willReturn("/api/v1/orders/1");

        entryPoint.commence(request, response, new InvalidBearerTokenException("expirado"));

        then(response).should().setHeader("WWW-Authenticate", "Bearer error=\"invalid_token\"");
    }

    @Test
    @DisplayName("Deve escapar aspas e barras invertidas no instance")
    void shouldEscapeQuotesAndBackslashesInInstance() throws Exception {
        given(request.getRequestURI()).willReturn("/a\"b\\c");

        entryPoint.commence(request, response, new InsufficientAuthenticationException("sem token"));

        assertThat(body.toString()).contains("\"instance\":\"/a\\\"b\\\\c\"");
    }

    @Test
    @DisplayName("Deve usar instance vazio quando a URI da requisição for nula")
    void shouldUseEmptyInstanceWhenRequestUriIsNull() throws Exception {
        given(request.getRequestURI()).willReturn(null);

        entryPoint.commence(request, response, new InsufficientAuthenticationException("sem token"));

        assertThat(body.toString()).contains("\"instance\":\"\"");
    }
}
