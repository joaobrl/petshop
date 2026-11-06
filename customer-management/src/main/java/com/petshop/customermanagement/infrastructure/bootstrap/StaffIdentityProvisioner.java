package com.petshop.customermanagement.infrastructure.bootstrap;

import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.out.IdentityProviderPortOut;
import com.petshop.customermanagement.core.port.out.dto.IdentityUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Garante, de forma idempotente, que um funcionário local existe no Keycloak (SDD-024 F3).
 * Usado pelos runners de bootstrap (ADMIN inicial) e de seed de desenvolvimento.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StaffIdentityProvisioner {

    private final IdentityProviderPortOut identityProviderPortOut;

    /**
     * Cria o usuário no Keycloak se ainda não houver um com o mesmo {@code user_id}.
     * {@code password} nulo/vazio = sem credencial + e-mail de ação UPDATE_PASSWORD.
     *
     * @return true se criou
     */
    public boolean ensure(Staff staff, String password) {
        if (identityProviderPortOut.findIdByUserId(staff.getId()).isPresent()) {
            return false;
        }
        if (identityProviderPortOut.findIdByEmail(staff.getEmail()).isPresent()) {
            log.warn("Keycloak already has another user with the e-mail of staff id={}; not provisioned.",
                    staff.getId());
            return false;
        }
        boolean hasPassword = password != null && !password.isBlank();
        var idpUserId = identityProviderPortOut.createUser(IdentityUser.of(staff), hasPassword ? password : null);
        if (!hasPassword) {
            try {
                identityProviderPortOut.sendSetPasswordEmail(idpUserId);
            } catch (RuntimeException e) {
                log.warn("Set-password email not sent for staff id={} ({}); use 'Esqueci a senha' no login.",
                        staff.getId(), e.getClass().getSimpleName());
            }
        }
        log.info("Staff provisioned in Keycloak: id={}, role={}", staff.getId(), staff.getRole());
        return true;
    }
}
