package com.petshop.customermanagement.infrastructure.bootstrap;

import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    private PasswordEncoder passwordEncoder;

    private BootstrapAdminRunner runner;

    @BeforeEach
    void setUp() {
        runner = new BootstrapAdminRunner(staffPortOut, passwordEncoder);
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
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-random-password");
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
        assertThat(admin.getPasswordHash()).isEqualTo("hashed-random-password");
        assertThat(admin.getMustChangePassword()).isTrue();
    }

    @Test
    void generatesARandomPasswordInsteadOfUsingTheCpf() throws Exception {
        when(staffPortOut.findAll()).thenReturn(List.of());
        when(staffPortOut.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        var generatedPassword = passwordCaptor.getValue();

        assertThat(generatedPassword).isNotEqualTo(CPF);
        assertThat(generatedPassword).hasSize(20);
    }

    @Test
    void usesTheConfiguredPasswordWhenPresentInsteadOfGeneratingOne() throws Exception {
        ReflectionTestUtils.setField(runner, "configuredPassword", "S3nhaF0rteConfigurada!");
        when(staffPortOut.findAll()).thenReturn(List.of());
        when(staffPortOut.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

        runner.run(null);

        verify(passwordEncoder).encode("S3nhaF0rteConfigurada!");
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
}
