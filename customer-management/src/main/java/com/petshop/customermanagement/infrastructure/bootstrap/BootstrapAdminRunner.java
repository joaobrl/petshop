package com.petshop.customermanagement.infrastructure.bootstrap;

import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%&*";
    private static final int GENERATED_PASSWORD_LENGTH = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StaffPortOut staffPortOut;
    private final PasswordEncoder passwordEncoder;

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
        if (!staffPortOut.findAll().isEmpty()) {
            return;
        }

        String rawPassword = StringUtils.hasText(configuredPassword) ? configuredPassword : generateRandomPassword();

        var admin = new Staff();
        admin.setId(UUID.randomUUID());
        admin.setName(name);
        admin.setEmail(email);
        admin.setCpf(cpf);
        admin.setPhone(phone);
        admin.setEnabled(true);
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setMustChangePassword(true);
        staffPortOut.save(admin);

        log.warn("Nenhum funcionário encontrado — ADMIN inicial criado. ", email);
        System.out.println("========================================================================");
        System.out.println("ADMIN inicial criado. Senha temporária (não será exibida novamente): " + rawPassword);
        System.out.println("========================================================================");
    }

    private String generateRandomPassword() {
        var sb = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return sb.toString();
    }
}
