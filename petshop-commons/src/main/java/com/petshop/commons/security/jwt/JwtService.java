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

import static com.petshop.commons.security.jwt.AuthenticatedUser.CLAIM_CPF;
import static com.petshop.commons.security.jwt.AuthenticatedUser.CLAIM_EMAIL;
import static com.petshop.commons.security.jwt.AuthenticatedUser.CLAIM_NAME;
import static com.petshop.commons.security.jwt.AuthenticatedUser.CLAIM_PHONE;
import static com.petshop.commons.security.jwt.AuthenticatedUser.CLAIM_ROLE;
import static com.petshop.commons.security.jwt.AuthenticatedUser.CLAIM_TYPE;

public class JwtService {

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

        return AuthenticatedUser.fromClaims(claims);
    }

    public long expirationSeconds() {
        return expiration.toSeconds();
    }
}
