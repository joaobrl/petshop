package com.petshop.customermanagement.infrastructure.bootstrap;

import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;
import java.util.UUID;

/**
 * ADMIN inicial (SDD-024 F3). Local: criado só quando a tabela de staff está vazia.
 * Keycloak: garantido a cada start, de forma idempotente — com BOOTSTRAP_ADMIN_PASSWORD
 * como credencial não temporária, ou, se vazia, sem senha e com o e-mail de ação
 * UPDATE_PASSWORD do Keycloak. Não existe senha provisória gerada.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminRunner implements ApplicationRunner {

    private final StaffPortOut staffPortOut;
    private final StaffIdentityProvisioner staffIdentityProvisioner;

    @Value("${bootstrap.admin.name}")
    private String name;

    @Value("${bootstrap.admin.email}")
    private String email;

    @Value("${bootstrap.admin.cpf}")
    private String cpf;

    @Value("${bootstrap.admin.phone}")
    private String phone;

    @Value("${bootstrap.admin.password:}")
    private String configuredPassword;

    @Override
    public void run(ApplicationArguments args) {
        var admin = staffPortOut.findByEmail(email).or(this::createLocalAdminIfNoStaff);
        if (admin.isEmpty()) {
            // Já há funcionários e o e-mail do bootstrap não é de nenhum: nada a garantir.
            return;
        }
        try {
            if (staffIdentityProvisioner.ensure(admin.get(), configuredPassword)) {
                log.warn("ADMIN inicial criado no Keycloak ({}).", StringUtils.hasText(configuredPassword)
                        ? "senha = BOOTSTRAP_ADMIN_PASSWORD"
                        : "sem senha: o Keycloak enviou o e-mail para definir a senha");
            }
        } catch (RuntimeException e) {
            // Não derruba o serviço: o próximo start tenta de novo.
            log.error("ADMIN inicial não garantido no Keycloak ({}); tentará de novo no próximo start.",
                    e.getClass().getSimpleName());
        }
    }

    private Optional<Staff> createLocalAdminIfNoStaff() {
        if (!staffPortOut.findAll().isEmpty()) {
            return Optional.empty();
        }
        var admin = new Staff(UUID.randomUUID(), name, cpf, email, phone, true, Role.ADMIN);
        log.warn("Nenhum funcionário encontrado — ADMIN inicial criado localmente.");
        staffPortOut.save(admin);
        return Optional.of(admin);
    }
}
