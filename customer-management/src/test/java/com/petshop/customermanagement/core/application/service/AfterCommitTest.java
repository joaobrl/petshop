package com.petshop.customermanagement.core.application.service;

import ch.qos.logback.classic.Level;
import com.petshop.customermanagement.support.LogCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@ExtendWith(MockitoExtension.class)
class AfterCommitTest {

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    @DisplayName("Deve executar a ação imediatamente quando não houver sincronização de transação ativa")
    void shouldRunActionImmediatelyWhenNoSynchronizationIsActive() {
        var runs = new AtomicInteger();

        AfterCommit.run(runs::incrementAndGet);

        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("Deve executar a ação somente no afterCommit quando houver sincronização ativa")
    void shouldRunActionOnlyOnAfterCommitWhenSynchronizationIsActive() {
        var runs = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(runs::incrementAndGet);

        assertThat(runs).hasValue(0);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(runs).hasValue(1);
    }

    @Test
    @DisplayName("Deve não executar a ação quando a transação sofrer rollback")
    void shouldNotRunActionWhenTransactionRollsBack() {
        var runs = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();

        AfterCommit.run(runs::incrementAndGet);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThat(runs).hasValue(0);
    }

    @Test
    @DisplayName("Deve apenas logar aviso sem dados pessoais quando a ação falhar após o commit")
    void shouldOnlyLogWarningWithoutPersonalDataWhenActionFailsAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        AfterCommit.run(() -> {
            throw new IllegalStateException("falha para maria@mail.com");
        });

        try (var logs = LogCaptor.of(AfterCommit.class)) {
            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit))
                    .doesNotThrowAnyException();

            assertThat(logs.has(Level.WARN, "After-commit action failed")).isTrue();
            assertThat(logs.events()).noneMatch(e -> e.getFormattedMessage().contains("maria@mail.com"));
        }
    }
}
