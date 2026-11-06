package com.petshop.customermanagement.infrastructure.bootstrap;

import ch.qos.logback.classic.Level;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.out.IdentityProviderPortOut;
import com.petshop.customermanagement.core.port.out.dto.IdentityUser;
import com.petshop.customermanagement.support.LogCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class StaffIdentityProvisionerTest {

    private static final String PASSWORD = "S3nhaF0rte!";

    @Mock
    private IdentityProviderPortOut identityProviderPortOut;

    private StaffIdentityProvisioner provisioner;
    private Staff staff;

    @BeforeEach
    void setUp() {
        provisioner = new StaffIdentityProvisioner(identityProviderPortOut);
        staff = new Staff(UUID.randomUUID(), "Carla Souza", "11111111111", "carla@petshop.dev", "11977771111", true,
                Role.RECEPTIONIST);
    }

    @Test
    @DisplayName("Deve retornar falso sem criar nada quando o usuário já existir no IdP pelo user_id")
    void shouldReturnFalseWithoutCreatingAnythingWhenUserAlreadyExistsByUserId() {
        given(identityProviderPortOut.findIdByUserId(staff.getId())).willReturn(Optional.of("kc-1"));

        var created = provisioner.ensure(staff, PASSWORD);

        assertThat(created).isFalse();
        then(identityProviderPortOut).should(never()).findIdByEmail(any());
        then(identityProviderPortOut).should(never()).createUser(any(), any());
        then(identityProviderPortOut).should(never()).sendSetPasswordEmail(any());
    }

    @Test
    @DisplayName("Deve retornar falso e logar aviso quando o e-mail pertencer a outro usuário do IdP")
    void shouldReturnFalseAndLogWarningWhenEmailBelongsToAnotherIdentityUser() {
        given(identityProviderPortOut.findIdByUserId(staff.getId())).willReturn(Optional.empty());
        given(identityProviderPortOut.findIdByEmail(staff.getEmail())).willReturn(Optional.of("kc-other"));

        try (var logs = LogCaptor.of(StaffIdentityProvisioner.class)) {
            var created = provisioner.ensure(staff, PASSWORD);

            assertThat(created).isFalse();
            assertThat(logs.has(Level.WARN, "another user with the e-mail")).isTrue();
        }
        then(identityProviderPortOut).should(never()).createUser(any(), any());
    }

    @Test
    @DisplayName("Deve criar o usuário com a senha sem enviar e-mail e retornar verdadeiro quando houver senha")
    void shouldCreateUserWithPasswordWithoutEmailAndReturnTrueWhenPasswordIsProvided() {
        givenStaffNotInIdentityProvider();
        given(identityProviderPortOut.createUser(any(IdentityUser.class), any())).willReturn("kc-1");

        var created = provisioner.ensure(staff, PASSWORD);

        assertThat(created).isTrue();
        var captor = ArgumentCaptor.forClass(IdentityUser.class);
        then(identityProviderPortOut).should().createUser(captor.capture(), org.mockito.ArgumentMatchers.eq(PASSWORD));
        assertThat(captor.getValue().userId()).isEqualTo(staff.getId());
        assertThat(captor.getValue().email()).isEqualTo("carla@petshop.dev");
        assertThat(captor.getValue().role()).isEqualTo(Role.RECEPTIONIST);
        assertThat(captor.getValue().type()).isEqualTo(AccountType.STAFF);
        then(identityProviderPortOut).should(never()).sendSetPasswordEmail(any());
    }

    @Test
    @DisplayName("Deve criar o usuário sem senha e enviar o e-mail de definição de senha quando a senha for vazia")
    void shouldCreateUserWithoutPasswordAndSendSetPasswordEmailWhenPasswordIsBlank() {
        givenStaffNotInIdentityProvider();
        given(identityProviderPortOut.createUser(any(IdentityUser.class), isNull())).willReturn("kc-1");

        var created = provisioner.ensure(staff, "  ");

        assertThat(created).isTrue();
        then(identityProviderPortOut).should().createUser(any(IdentityUser.class), isNull());
        then(identityProviderPortOut).should().sendSetPasswordEmail("kc-1");
    }

    @Test
    @DisplayName("Deve criar o usuário sem senha e enviar o e-mail quando a senha for nula")
    void shouldCreateUserWithoutPasswordAndSendSetPasswordEmailWhenPasswordIsNull() {
        givenStaffNotInIdentityProvider();
        given(identityProviderPortOut.createUser(any(IdentityUser.class), isNull())).willReturn("kc-1");

        var created = provisioner.ensure(staff, null);

        assertThat(created).isTrue();
        then(identityProviderPortOut).should().sendSetPasswordEmail("kc-1");
    }

    @Test
    @DisplayName("Deve logar aviso e retornar verdadeiro quando o envio do e-mail de senha falhar")
    void shouldLogWarningAndReturnTrueWhenSetPasswordEmailFails() {
        givenStaffNotInIdentityProvider();
        given(identityProviderPortOut.createUser(any(IdentityUser.class), isNull())).willReturn("kc-1");
        willThrow(new IllegalStateException("smtp fora")).given(identityProviderPortOut).sendSetPasswordEmail("kc-1");

        try (var logs = LogCaptor.of(StaffIdentityProvisioner.class)) {
            var created = provisioner.ensure(staff, "");

            assertThat(created).isTrue();
            assertThat(logs.has(Level.WARN, "Set-password email not sent")).isTrue();
        }
    }

    private void givenStaffNotInIdentityProvider() {
        given(identityProviderPortOut.findIdByUserId(staff.getId())).willReturn(Optional.empty());
        given(identityProviderPortOut.findIdByEmail(staff.getEmail())).willReturn(Optional.empty());
    }
}
