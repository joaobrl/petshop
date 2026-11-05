package com.petshop.customermanagement.core.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Token de verificação de e-mail (SDD-020). Só o hash SHA-256 do valor
 * enviado no link é guardado; o valor em claro existe apenas no e-mail.
 * {@code usedAt} preenchido = token já consumido.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class EmailVerificationToken {

    private UUID id;
    private UUID customerId;
    private String tokenHash;
    private Instant createdAt;
    private Instant expiresAt;
    private Instant usedAt;

    public EmailVerificationToken(UUID customerId, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
