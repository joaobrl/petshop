package com.petshop.customermanagement.infrastructure.bootstrap;

import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import ch.qos.logback.classic.Level;
import com.petshop.commons.exception.DependencyUnavailableException;
import com.petshop.customermanagement.support.LogCaptor;
import org.junit.jupiter.api.DisplayName;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * As propriedades bootstrap.admin.* (name/email/cpf/phone/password) vêm do
 * application.yaml via @Value — aqui são injetadas direto nos campos com
 * ReflectionTestUtils, sem subir contexto Spring.
 */
@ExtendWith(MockitoExtension.class)
class BootstrapAdminRunnerTest {

    private static final String NAME = "Admin Petshop";
    private static final String EMAIL = "admin@petshop.local";
    private static final String CPF = "00000000000";
    private static final String PHONE = "00000000000";

    @Mock
    private StaffPortOut staffPortOut;

    @Mock
    private StaffIdentityProvisioner staffIdentityProvisioner;

    private BootstrapAdminRunner runner;

    @BeforeEach
    void setUp() {
        runner = new BootstrapAdminRunner(staffPortOut, staffIdentityProvisioner);
        ReflectionTestUtils.setField(runner, "name", NAME);
        ReflectionTestUtils.setField(runner, "email", EMAIL);
        ReflectionTestUtils.setField(runner, "cpf", CPF);
        ReflectionTestUtils.setField(runner, "phone", PHONE);
        ReflectionTestUtils.setField(runner, "configuredPassword", "");
    }

    @Test
    void doesNothingWhenStaffAlreadyExists() throws Exception {
        when(staffPortOut.findAll()).thenReturn(List.of(new Staff()));

        runner.run(null);

        verify(staffPortOut, never()).save(any());
    }

    @Test
    void createsAdminWhenNoStaffExists() throws Exception {
        when(staffPortOut.findAll()).thenReturn(List.of());
        when(staffPortOut.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class);
        verify(staffPortOut).save(captor.capture());
        var admin = captor.getValue();

        assertThat(admin.getId()).isNotNull();
        assertThat(admin.getName()).isEqualTo(NAME);
        assertThat(admin.getEmail()).isEqualTo(EMAIL);
        assertThat(admin.getCpf()).isEqualTo(CPF);
        assertThat(admin.getPhone()).isEqualTo(PHONE);
        assertThat(admin.getEnabled()).isTrue();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        // Sem senha provisória (SDD-024 F3): BOOTSTRAP_ADMIN_PASSWORD vazia = Keycloak sem senha.
        verify(staffIdentityProvisioner).ensure(admin, "");
    }

    @Test
    void usesTheConfiguredPasswordAsKeycloakCredentialWhenPresent() throws Exception {
        ReflectionTestUtils.setField(runner, "configuredPassword", "S3nhaF0rteConfigurada!");
        when(staffPortOut.findAll()).thenReturn(List.of());
        when(staffPortOut.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        verify(staffIdentityProvisioner).ensure(any(Staff.class), org.mockito.ArgumentMatchers.eq("S3nhaF0rteConfigurada!"));
    }

    @Test
    void generatesADifferentIdOnEachRunWhenInvokedAgain() throws Exception {
        when(staffPortOut.findAll()).thenReturn(List.of());
        when(staffPortOut.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

        ArgumentCaptor<Staff> captor = ArgumentCaptor.forClass(Staff.class);

        runner.run(null);
        runner.run(null);

        verify(staffPortOut, times(2)).save(captor.capture());
        var ids = captor.getAllValues().stream().map(Staff::getId).toList();

        assertThat(ids.get(0)).isNotEqualTo(ids.get(1));
    }

    @Test
    @DisplayName("Deve criar o admin local e garantir no IdP com senha vazia quando não houver funcionários")
    void shouldCreateLocalAdminAndEnsureInIdentityProviderWhenNoStaffExists() {
        given(staffPortOut.findAll()).willReturn(List.of());
        given(staffPortOut.save(any(Staff.class))).willAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        var captor = ArgumentCaptor.forClass(Staff.class);
        then(staffPortOut).should().save(captor.capture());
        then(staffIdentityProvisioner).should().ensure(captor.getValue(), "");
    }

    @Test
    @DisplayName("Deve não criar admin local nem garantir no IdP quando já houver funcionários e o e-mail não for do bootstrap")
    void shouldNotCreateLocalAdminNorEnsureInIdentityProviderWhenStaffExistAndEmailIsNotBootstrap() {
        given(staffPortOut.findAll()).willReturn(List.of(new Staff()));

        runner.run(null);

        then(staffPortOut).should(never()).save(any());
        then(staffIdentityProvisioner).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("Deve garantir no IdP o admin existente sem criar outro local quando o e-mail do bootstrap já existir")
    void shouldEnsureExistingAdminInIdentityProviderWithoutCreatingLocalOneWhenBootstrapEmailExists() {
        var existing = new Staff(java.util.UUID.randomUUID(), NAME, CPF, EMAIL, PHONE, true, Role.ADMIN);
        given(staffPortOut.findByEmail(EMAIL)).willReturn(java.util.Optional.of(existing));

        runner.run(null);

        then(staffPortOut).should(never()).save(any());
        then(staffPortOut).should(never()).findAll();
        then(staffIdentityProvisioner).should().ensure(existing, "");
    }

    @Test
    @DisplayName("Deve logar aviso de senha configurada quando o IdP criar o admin com senha")
    void shouldLogConfiguredPasswordWarningWhenIdentityProviderCreatesAdminWithPassword() {
        ReflectionTestUtils.setField(runner, "configuredPassword", "S3nhaF0rte!");
        given(staffPortOut.findAll()).willReturn(List.of());
        given(staffPortOut.save(any(Staff.class))).willAnswer(inv -> inv.getArgument(0));
        given(staffIdentityProvisioner.ensure(any(Staff.class), org.mockito.ArgumentMatchers.eq("S3nhaF0rte!")))
                .willReturn(true);

        try (var logs = LogCaptor.of(BootstrapAdminRunner.class)) {
            runner.run(null);

            assertThat(logs.has(Level.WARN, "senha = BOOTSTRAP_ADMIN_PASSWORD")).isTrue();
        }
    }

    @Test
    @DisplayName("Deve logar aviso de e-mail para definir senha quando o IdP criar o admin sem senha")
    void shouldLogSetPasswordEmailWarningWhenIdentityProviderCreatesAdminWithoutPassword() {
        given(staffPortOut.findAll()).willReturn(List.of());
        given(staffPortOut.save(any(Staff.class))).willAnswer(inv -> inv.getArgument(0));
        given(staffIdentityProvisioner.ensure(any(Staff.class), org.mockito.ArgumentMatchers.eq(""))).willReturn(true);

        try (var logs = LogCaptor.of(BootstrapAdminRunner.class)) {
            runner.run(null);

            assertThat(logs.has(Level.WARN, "sem senha")).isTrue();
        }
    }

    @Test
    @DisplayName("Deve não logar criação no IdP quando o admin já existir no IdP")
    void shouldNotLogIdentityProviderCreationWhenAdminAlreadyExistsInIdentityProvider() {
        given(staffPortOut.findAll()).willReturn(List.of());
        given(staffPortOut.save(any(Staff.class))).willAnswer(inv -> inv.getArgument(0));
        given(staffIdentityProvisioner.ensure(any(Staff.class), org.mockito.ArgumentMatchers.eq(""))).willReturn(false);

        try (var logs = LogCaptor.of(BootstrapAdminRunner.class)) {
            runner.run(null);

            assertThat(logs.has(Level.WARN, "ADMIN inicial criado no Keycloak")).isFalse();
        }
    }

    @Test
    @DisplayName("Deve logar erro e não lançar exceção quando o IdP estiver fora")
    void shouldLogErrorAndNotThrowWhenIdentityProviderIsDown() {
        given(staffPortOut.findAll()).willReturn(List.of());
        given(staffPortOut.save(any(Staff.class))).willAnswer(inv -> inv.getArgument(0));
        given(staffIdentityProvisioner.ensure(any(Staff.class), any()))
                .willThrow(new DependencyUnavailableException("keycloak", new IllegalStateException("fora")));

        try (var logs = LogCaptor.of(BootstrapAdminRunner.class)) {
            org.assertj.core.api.Assertions.assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();

            assertThat(logs.has(Level.ERROR, "ADMIN inicial não garantido no Keycloak")).isTrue();
        }
    }
}
