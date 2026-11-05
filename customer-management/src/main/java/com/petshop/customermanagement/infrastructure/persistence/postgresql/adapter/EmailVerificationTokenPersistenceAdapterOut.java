package com.petshop.customermanagement.infrastructure.persistence.postgresql.adapter;

import com.petshop.customermanagement.core.domain.EmailVerificationToken;
import com.petshop.customermanagement.core.port.out.EmailVerificationTokenPortOut;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.EmailVerificationTokenEntity;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.repository.EmailVerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class EmailVerificationTokenPersistenceAdapterOut implements EmailVerificationTokenPortOut {

    private final EmailVerificationTokenRepository repository;

    @Override
    public EmailVerificationToken save(EmailVerificationToken token) {
        var entity = toEntity(token);
        if (repository.existsById(entity.getId())) {
            entity.setNew(false);
        }
        return toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmailVerificationToken> findByTokenHash(String tokenHash) {
        return repository.findByTokenHash(tokenHash).map(EmailVerificationTokenPersistenceAdapterOut::toDomain);
    }

    @Override
    @Transactional
    public boolean markUsed(UUID tokenId, Instant usedAt) {
        return repository.markUsed(tokenId, usedAt) == 1;
    }

    @Override
    @Transactional
    public void deleteUnusedByCustomerId(UUID customerId) {
        repository.deleteUnusedByCustomerId(customerId);
    }

    private static EmailVerificationTokenEntity toEntity(EmailVerificationToken token) {
        var entity = new EmailVerificationTokenEntity();
        entity.setId(token.getId());
        entity.setCustomerId(token.getCustomerId());
        entity.setTokenHash(token.getTokenHash());
        entity.setCreatedAt(token.getCreatedAt());
        entity.setExpiresAt(token.getExpiresAt());
        entity.setUsedAt(token.getUsedAt());
        return entity;
    }

    private static EmailVerificationToken toDomain(EmailVerificationTokenEntity entity) {
        return new EmailVerificationToken(entity.getId(), entity.getCustomerId(), entity.getTokenHash(),
                entity.getCreatedAt(), entity.getExpiresAt(), entity.getUsedAt());
    }
}
