package com.petshop.customermanagement.infrastructure.bootstrap;

import ch.qos.logback.classic.Level;
import com.petshop.commons.exception.DependencyUnavailableException;
import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import com.petshop.customermanagement.support.LogCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DevStaffSeedRunnerTest {

    private static final String PASSWORD = "dev-password";

    @Mock
    private StaffPortOut staffPortOut;

    @Mock
    private StaffIdentityProvisioner staffIdentityProvisioner;

    private DevStaffSeedRunner runner;

    @BeforeEach
    void setUp() {
        runner = new DevStaffSeedRunner(staffPortOut, staffIdentityProvisioner);
        ReflectionTestUtils.setField(runner, "password", PASSWORD);
    }

    @Test
    @DisplayName("Deve criar os três funcionários do seed localmente e no IdP quando nenhum existir")
    void shouldCreateTheThreeSeedStaffLocallyAndInIdentityProviderWhenNoneExists() {
        given(staffPortOut.save(any(Staff.class))).willAnswer(inv -> inv.getArgument(0));
        given(staffIdentityProvisioner.ensure(any(Staff.class), eq(PASSWORD))).willReturn(true);

        runner.run(null);

        var saved = ArgumentCaptor.forClass(Staff.class);
        then(staffPortOut).should(times(3)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Staff::getEmail)
                .containsExactly("carla@petshop.dev", "paulo@petshop.dev", "henrique@petshop.dev");
        assertThat(saved.getAllValues()).extracting(Staff::getRole)
                .containsExactly(Role.RECEPTIONIST, Role.GROOMER, Role.VETERINARIAN);
        assertThat(saved.getAllValues()).allSatisfy(s -> {
            assertThat(s.getId()).isNotNull();
            assertThat(s.getEnabled()).isTrue();
        });
        then(staffIdentityProvisioner).should(times(3)).ensure(any(Staff.class), eq(PASSWORD));
    }

    @Test
    @DisplayName("Deve ser idempotente e não salvar novamente quando os funcionários já existirem")
    void shouldBeIdempotentAndNotSaveAgainWhenStaffAlreadyExist() {
        given(staffPortOut.findByEmail(any())).willAnswer(inv -> Optional.of(new Staff(UUID.randomUUID(), "Existente",
                "00000000000", inv.getArgument(0), "11900000000", true, Role.RECEPTIONIST)));
        given(staffIdentityProvisioner.ensure(any(Staff.class), eq(PASSWORD))).willReturn(false);

        runner.run(null);

        then(staffPortOut).should(never()).save(any());
        then(staffIdentityProvisioner).should(times(3)).ensure(any(Staff.class), eq(PASSWORD));
    }

    @Test
    @DisplayName("Deve logar o erro e seguir para os próximos itens quando um funcionário do seed falhar")
    void shouldLogErrorAndContinueWithNextItemsWhenOneSeedStaffFails() {
        given(staffPortOut.save(any(Staff.class))).willAnswer(inv -> inv.getArgument(0));
        given(staffIdentityProvisioner.ensure(any(Staff.class), eq(PASSWORD)))
                .willThrow(new DependencyUnavailableException("keycloak", new IllegalStateException("fora")))
                .willReturn(true);

        try (var logs = LogCaptor.of(DevStaffSeedRunner.class)) {
            runner.run(null);

            assertThat(logs.has(Level.ERROR, "carla@petshop.dev falhou")).isTrue();
            assertThat(logs.has(Level.INFO, "paulo@petshop.dev")).isTrue();
            assertThat(logs.has(Level.INFO, "henrique@petshop.dev")).isTrue();
        }
        then(staffIdentityProvisioner).should(times(3)).ensure(any(Staff.class), eq(PASSWORD));
    }
}
