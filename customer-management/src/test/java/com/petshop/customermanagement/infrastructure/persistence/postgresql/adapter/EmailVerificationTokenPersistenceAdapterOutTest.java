package com.petshop.customermanagement.infrastructure.persistence.postgresql.adapter;

import com.petshop.customermanagement.core.domain.EmailVerificationToken;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.entity.EmailVerificationTokenEntity;
import com.petshop.customermanagement.infrastructure.persistence.postgresql.repository.EmailVerificationTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationTokenPersistenceAdapterOutTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-01-02T10:00:00Z");
    private static final Instant USED_AT = Instant.parse("2026-01-01T12:00:00Z");

    @Mock
    private EmailVerificationTokenRepository repository;

    private EmailVerificationTokenPersistenceAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new EmailVerificationTokenPersistenceAdapterOut(repository);
    }

    private static EmailVerificationTokenEntity entity(UUID id, UUID customerId) {
        var entity = new EmailVerificationTokenEntity();
        entity.setId(id);
        entity.setCustomerId(customerId);
        entity.setTokenHash("hash");
        entity.setCreatedAt(CREATED_AT);
        entity.setExpiresAt(EXPIRES_AT);
        entity.setUsedAt(USED_AT);
        return entity;
    }

    @Test
    @DisplayName("Deve salvar como entidade nova e mapear de volta quando o token ainda não existir")
    void shouldSaveAsNewEntityAndMapBackWhenTokenDoesNotExist() {
        var token = new EmailVerificationToken(UUID.randomUUID(), "hash", CREATED_AT, EXPIRES_AT);
        when(repository.existsById(token.getId())).thenReturn(false);
        when(repository.save(any(EmailVerificationTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = adapter.save(token);

        var captor = ArgumentCaptor.forClass(EmailVerificationTokenEntity.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.isNew()).isTrue();
        assertThat(saved.getId()).isEqualTo(token.getId());
        assertThat(saved.getCustomerId()).isEqualTo(token.getCustomerId());
        assertThat(saved.getTokenHash()).isEqualTo("hash");
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(saved.getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(saved.getUsedAt()).isNull();
        assertThat(result.getId()).isEqualTo(token.getId());
        assertThat(result.getTokenHash()).isEqualTo("hash");
    }

    @Test
    @DisplayName("Deve salvar como entidade existente quando o token já existir")
    void shouldSaveAsExistingEntityWhenTokenAlreadyExists() {
        var token = new EmailVerificationToken(UUID.randomUUID(), UUID.randomUUID(), "hash", CREATED_AT, EXPIRES_AT, USED_AT);
        when(repository.existsById(token.getId())).thenReturn(true);
        when(repository.save(any(EmailVerificationTokenEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = adapter.save(token);

        var captor = ArgumentCaptor.forClass(EmailVerificationTokenEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isNew()).isFalse();
        assertThat(captor.getValue().getUsedAt()).isEqualTo(USED_AT);
        assertThat(result.getUsedAt()).isEqualTo(USED_AT);
    }

    @Test
    @DisplayName("Deve mapear a entidade para domínio quando o hash existir")
    void shouldMapEntityToDomainWhenHashExists() {
        var id = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        when(repository.findByTokenHash("hash")).thenReturn(Optional.of(entity(id, customerId)));

        var result = adapter.findByTokenHash("hash");

        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(id);
        assertThat(result.get().getCustomerId()).isEqualTo(customerId);
        assertThat(result.get().getTokenHash()).isEqualTo("hash");
        assertThat(result.get().getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(result.get().getExpiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(result.get().getUsedAt()).isEqualTo(USED_AT);
    }

    @Test
    @DisplayName("Deve retornar vazio quando o hash não existir")
    void shouldReturnEmptyWhenHashDoesNotExist() {
        when(repository.findByTokenHash("missing")).thenReturn(Optional.empty());

        assertThat(adapter.findByTokenHash("missing")).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar true quando o update afetar uma linha")
    void shouldReturnTrueWhenMarkUsedUpdatesOneRow() {
        var id = UUID.randomUUID();
        when(repository.markUsed(id, USED_AT)).thenReturn(1);

        assertThat(adapter.markUsed(id, USED_AT)).isTrue();
    }

    @Test
    @DisplayName("Deve retornar false quando o update não afetar nenhuma linha")
    void shouldReturnFalseWhenMarkUsedUpdatesNoRows() {
        var id = UUID.randomUUID();
        when(repository.markUsed(id, USED_AT)).thenReturn(0);

        assertThat(adapter.markUsed(id, USED_AT)).isFalse();
    }

    @Test
    @DisplayName("Deve delegar ao repositório quando remover tokens não usados do cliente")
    void shouldDelegateToRepositoryWhenDeletingUnusedTokensByCustomerId() {
        var customerId = UUID.randomUUID();

        adapter.deleteUnusedByCustomerId(customerId);

        verify(repository).deleteUnusedByCustomerId(customerId);
    }
}
