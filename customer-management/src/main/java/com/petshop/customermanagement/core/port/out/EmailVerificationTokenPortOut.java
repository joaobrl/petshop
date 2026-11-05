package com.petshop.customermanagement.core.port.out;

import com.petshop.customermanagement.core.domain.EmailVerificationToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenPortOut {

    EmailVerificationToken save(EmailVerificationToken token);

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    /**
     * Marca o token como usado só se ainda não estiver — update condicional,
     * para que duas confirmações simultâneas do mesmo link não passem as duas.
     *
     * @return true se este chamador consumiu o token
     */
    boolean markUsed(UUID tokenId, Instant usedAt);

    /** Remove os tokens ainda não usados do cliente (reenvio invalida os anteriores). */
    void deleteUnusedByCustomerId(UUID customerId);
}
