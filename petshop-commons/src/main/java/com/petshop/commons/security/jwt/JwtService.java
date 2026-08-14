package com.petshop.commons.security.jwt;

import com.petshop.commons.security.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public class JwtService {

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_CPF = "cpf";
    private static final String CLAIM_PHONE = "phone";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";

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
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subjectId.toString())
                .claim(CLAIM_EMAIL, email)
                .claim(CLAIM_NAME, name)
                .claim(CLAIM_CPF, cpf)
                .claim(CLAIM_PHONE, phone)
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, type.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public String generateServiceToken(String serviceName) {
        var subjectId = UUID.nameUUIDFromBytes(serviceName.getBytes(StandardCharsets.UTF_8));
        return generateToken(subjectId, serviceName + "@internal", null, null, null, Role.SERVICE, AccountType.SERVICE);
    }

    public AuthenticatedUser parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return new AuthenticatedUser(
                UUID.fromString(claims.getSubject()),
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_NAME, String.class),
                claims.get(CLAIM_CPF, String.class),
                claims.get(CLAIM_PHONE, String.class),
                Role.valueOf(claims.get(CLAIM_ROLE, String.class)),
                AccountType.valueOf(claims.get(CLAIM_TYPE, String.class))
        );
    }

    public long expirationSeconds() {
        return expiration.toSeconds();
    }
}
