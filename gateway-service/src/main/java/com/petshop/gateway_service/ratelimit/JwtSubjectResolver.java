package com.petshop.gateway_service.ratelimit;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Extrai o {@code sub} de um {@code Authorization: Bearer} só se a assinatura
 * HS256 bater com o {@code JWT_SECRET} dos serviços (SDD-022). Serve apenas
 * para escolher a chave do rate limit — sem validar a assinatura, qualquer
 * um forjaria um {@code sub} novo a cada requisição e nunca seria limitado.
 *
 * <p>Não autentica nada: token ausente, inválido ou expirado só faz a chave
 * cair para o IP. Quem rejeita a requisição continua sendo cada serviço.
 */
@Component
public class JwtSubjectResolver {

    private static final Logger log = LoggerFactory.getLogger(JwtSubjectResolver.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;

    public JwtSubjectResolver(@Value("${jwt.secret:}") String secret) {
        this.key = buildKey(secret);
    }

    private static SecretKey buildKey(String secret) {
        // Diferente dos serviços, o gateway não falha a subida sem segredo:
        // ele não autentica ninguém, só perde a chave por usuário.
        if (secret == null || secret.isBlank()) {
            log.warn("Rate limit: JWT_SECRET não configurado — todas as requisições serão limitadas por IP");
            return null;
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            log.warn("Rate limit: JWT_SECRET com menos de {} bytes (HS256) — todas as requisições serão limitadas por IP",
                    MIN_SECRET_BYTES);
            return null;
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /**
     * @return o {@code sub} do token, vazio se não houver Bearer ou se o token
     *         for inválido/expirado
     */
    public Optional<String> resolveSubject(String authorizationHeader) {
        if (key == null || authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(authorizationHeader.substring(BEARER_PREFIX.length()))
                    .getPayload()
                    .getSubject();
            return Optional.ofNullable(subject).filter(s -> !s.isBlank());
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
