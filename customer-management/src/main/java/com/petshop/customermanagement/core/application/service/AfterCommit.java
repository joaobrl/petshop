package com.petshop.customermanagement.core.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Efeitos externos (publicação de e-mails) só depois do commit: se a transação desfizer,
 * nada é enviado para uma conta que não existe. Sem transação ativa, executa na hora.
 * Falha na ação não é relançada: a conta já foi gravada.
 */
@Slf4j
final class AfterCommit {

    private AfterCommit() {
    }

    static void run(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    action.run();
                } catch (RuntimeException e) {
                    log.warn("After-commit action failed ({})", e.getClass().getSimpleName());
                }
            }
        });
    }
}
