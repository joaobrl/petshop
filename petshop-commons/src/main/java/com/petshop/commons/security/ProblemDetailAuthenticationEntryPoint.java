package com.petshop.commons.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Responde 401 com corpo ProblemDetail (RFC 9457) quando a requisição não tem token
 * ou o token é inválido/expirado/de outro emissor ou audience (SDD-024, SDD-034).
 *
 * <p>Registrado tanto no {@code exceptionHandling} (rota protegida sem token) quanto no
 * {@code oauth2ResourceServer} (token presente mas recusado pelo JwtDecoder). Escreve
 * direto na response, sem {@code sendError}, pelo mesmo motivo do
 * {@link ProblemDetailAccessDeniedHandler}.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    static final String TITLE = "Não autenticado";
    static final String DETAIL = "Token de acesso ausente, inválido ou expirado.";

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String body = "{\"type\":\"about:blank\""
                + ",\"title\":\"" + TITLE + "\""
                + ",\"status\":" + HttpStatus.UNAUTHORIZED.value()
                + ",\"detail\":\"" + DETAIL + "\""
                + ",\"instance\":\"" + escape(request.getRequestURI()) + "\"}";

        // RFC 6750: token presente e recusado -> error="invalid_token"; sem token, só o esquema.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                authException instanceof OAuth2AuthenticationException ? "Bearer error=\"invalid_token\"" : "Bearer");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
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
