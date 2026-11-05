package com.petshop.commons.security.jwt;

import com.petshop.commons.security.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * EMISSÃO legada do token HS256 do login do customer-management. Temporário: sai na
 * SDD-024 F3 (login via Keycloak). Desde a F2 nenhum serviço valida esse token (o
 * Resource Server só aceita RS256 do Keycloak) e não há mais token de serviço assinado
 * aqui (F4: Client Credentials).
 */
public class JwtService {

    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_NAME = "name";
    static final String CLAIM_CPF = "cpf";
    static final String CLAIM_PHONE = "phone";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_TYPE = "type";
    static final String CLAIM_EMAIL_VERIFIED = AuthenticatedUser.CLAIM_EMAIL_VERIFIED;

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(String secret, Duration expiration) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET não configurado.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET precisa ter pelo menos 32 bytes (HS256).");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generateToken(UUID subjectId, String email, String name, String cpf, String phone, Role role, AccountType type) {
        return generateToken(subjectId, email, name, cpf, phone, role, type, null);
    }

    /**
     * {@code emailVerified} nulo = token sem a claim {@value AuthenticatedUser#CLAIM_EMAIL_VERIFIED}
     * (staff): o jjwt descarta claims com valor nulo.
     */
    public String generateToken(UUID subjectId, String email, String name, String cpf, String phone, Role role, AccountType type,
                                Boolean emailVerified) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subjectId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_CPF, cpf)
                .claim(CLAIM_PHONE, phone)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, type.name())
                .claim(CLAIM_EMAIL_VERIFIED, emailVerified)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public long expirationSeconds() {
        return expiration.toSeconds();
    }
}
