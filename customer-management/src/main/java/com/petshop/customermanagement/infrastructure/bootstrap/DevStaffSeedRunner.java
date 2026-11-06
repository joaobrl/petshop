package com.petshop.customermanagement.infrastructure.bootstrap;

import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Seed de funcionários de desenvolvimento (SDD-024 F3), ativo SÓ no profile {@code dev}
 * (SPRING_PROFILES_ACTIVE=dev): cria, de forma idempotente, os funcionários abaixo no banco
 * local e no Keycloak com a senha de dev {@code dev.seed.staff-password} (documentada no
 * README do módulo). Substitui o antigo POST /staff/create + login por senha do
 * web/scripts/seed-backend.mjs: não há password grant no realm.
 */
@Component
@Profile("dev")
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DevStaffSeedRunner implements ApplicationRunner {

    record SeedStaff(String name, String cpf, String email, String phone, Role role) { }

    static final List<SeedStaff> SEED = List.of(
            new SeedStaff("Carla Souza", "11111111111", "carla@petshop.dev", "11977771111", Role.RECEPTIONIST),
            new SeedStaff("Paulo Lima", "22222222222", "paulo@petshop.dev", "11977772222", Role.GROOMER),
            new SeedStaff("Henrique Costa", "44444444444", "henrique@petshop.dev", "11977774444", Role.VETERINARIAN));

    private final StaffPortOut staffPortOut;
    private final StaffIdentityProvisioner staffIdentityProvisioner;

    @Value("${dev.seed.staff-password}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        for (var seed : SEED) {
            try {
                var staff = staffPortOut.findByEmail(seed.email())
                        .orElseGet(() -> staffPortOut.save(new Staff(UUID.randomUUID(), seed.name(), seed.cpf(),
                                seed.email(), seed.phone(), true, seed.role())));
                boolean created = staffIdentityProvisioner.ensure(staff, password);
                log.info("Dev seed staff {} ({}): {}", seed.email(), seed.role(), created ? "criado" : "já existia");
            } catch (RuntimeException e) {
                log.error("Dev seed staff {} falhou: {}", seed.email(), e.getClass().getSimpleName());
            }
        }
    }
}
