package com.petshop.customermanagement.core.application.service;

import ch.qos.logback.classic.Level;
import com.petshop.customermanagement.core.port.out.IdentityProviderPortOut;
import com.petshop.customermanagement.support.LogCaptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class IdentityCompensationTest {

    private static final String IDP_USER_ID = "kc-123";

    @Mock
    private IdentityProviderPortOut identityProvider;

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    @Test
    @DisplayName("Deve não registrar nada quando não houver sincronização de transação ativa")
    void shouldNotRegisterAnythingWhenNoSynchronizationIsActive() {
        IdentityCompensation.deleteOnRollback(identityProvider, IDP_USER_ID);

        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        then(identityProvider).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Deve não apagar o usuário do IdP quando a transação for confirmada")
    void shouldNotDeleteIdentityUserWhenTransactionCommits() {
        TransactionSynchronizationManager.initSynchronization();
        IdentityCompensation.deleteOnRollback(identityProvider, IDP_USER_ID);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        then(identityProvider).should(never()).deleteUser(IDP_USER_ID);
    }

    @Test
    @DisplayName("Deve apagar o usuário do IdP quando a transação sofrer rollback")
    void shouldDeleteIdentityUserWhenTransactionRollsBack() {
        TransactionSynchronizationManager.initSynchronization();
        IdentityCompensation.deleteOnRollback(identityProvider, IDP_USER_ID);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        then(identityProvider).should().deleteUser(IDP_USER_ID);
    }

    @Test
    @DisplayName("Deve apagar o usuário do IdP quando o status da transação for desconhecido")
    void shouldDeleteIdentityUserWhenTransactionStatusIsUnknown() {
        TransactionSynchronizationManager.initSynchronization();
        IdentityCompensation.deleteOnRollback(identityProvider, IDP_USER_ID);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN));

        then(identityProvider).should().deleteUser(IDP_USER_ID);
    }

    @Test
    @DisplayName("Deve apenas registrar log de erro quando a remoção no IdP falhar durante o rollback")
    void shouldOnlyLogErrorWhenIdentityUserDeletionFailsOnRollback() {
        willThrow(new IllegalStateException("keycloak fora")).given(identityProvider).deleteUser(IDP_USER_ID);
        TransactionSynchronizationManager.initSynchronization();
        IdentityCompensation.deleteOnRollback(identityProvider, IDP_USER_ID);

        try (var logs = LogCaptor.of(IdentityCompensation.class)) {
            assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                    .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)))
                    .doesNotThrowAnyException();

            assertThat(logs.has(Level.ERROR, "Compensation failed")).isTrue();
        }
        then(identityProvider).should().deleteUser(IDP_USER_ID);
    }
}
