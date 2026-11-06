package com.petshop.customermanagement.core.application.service;

import com.petshop.customermanagement.core.port.out.IdentityProviderPortOut;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Compensação do cadastro no provedor de identidade (SDD-024 F3): o usuário é criado no
 * Keycloak dentro da transação local, antes do commit. Se a transação não chegar ao commit
 * (constraint violada no flush, por exemplo), o usuário recém-criado é removido do
 * Keycloak — nenhum dos dois lados fica com cadastro órfão.
 */
@Slf4j
final class IdentityCompensation {

    private IdentityCompensation() {
    }

    static void deleteOnRollback(IdentityProviderPortOut identityProvider, String idpUserId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    identityProvider.deleteUser(idpUserId);
                } catch (RuntimeException e) {
                    log.error("Compensation failed: identity provider user left without local record: idpUserId={}",
                            idpUserId, e);
                }
            }
        });
    }
}
