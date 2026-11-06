package com.petshop.customermanagement.core.application.service;

import com.petshop.commons.exception.ConflictException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.commons.security.Role;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.in.dto.StaffRequestDto;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.petshop.customermanagement.core.port.out.IdentityProviderPortOut;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import ch.qos.logback.classic.Level;
import com.petshop.customermanagement.core.port.out.dto.IdentityUser;
import com.petshop.customermanagement.support.LogCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock
    private StaffPortOut staffPortOut;
    @Mock
    private IdentityProviderPortOut identityProviderPortOut;

    private StaffService staffService;

    @BeforeEach
    void setUp() {
        staffService = new StaffService(staffPortOut, identityProviderPortOut);
    }

    private StaffRequestDto sampleRequest() {
        var request = new StaffRequestDto();
        request.setName("João");
        request.setCpf("12345678900");
        request.setEmail("joao@petshop.com");
        request.setPhone("11999999999");
        request.setRole(Role.RECEPTIONIST);
        return request;
    }

    private Staff sampleStaff(UUID id) {
        return new Staff(id, "João", "12345678900", "joao@petshop.com", "11999999999", true, Role.RECEPTIONIST);
    }

    @Nested
    class CreateStaff {

        @Test
        void savesStaffWithoutPasswordAndSendsSetPasswordEmail() {
            var request = sampleRequest();
            when(staffPortOut.findByCpf(request.getCpf())).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail(request.getEmail())).thenReturn(Optional.empty());
            when(staffPortOut.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var result = staffService.createStaff(request);

            assertThat(result.getName()).isEqualTo("João");
            assertThat(result.getCpf()).isEqualTo("12345678900");
            // Sem senha provisória (SDD-024 F3): Keycloak sem credencial + e-mail UPDATE_PASSWORD.
            verify(identityProviderPortOut).createUser(any(), org.mockito.ArgumentMatchers.isNull());
            verify(identityProviderPortOut).sendSetPasswordEmail(any());
            assertThat(result.getEnabled()).isTrue();

            var captor = ArgumentCaptor.forClass(Staff.class);
            verify(staffPortOut).save(captor.capture());
            assertThat(captor.getValue().getId()).isNotNull();
        }

        @Test
        void rejectsDuplicateCpf() {
            var request = sampleRequest();
            when(staffPortOut.findByCpf(request.getCpf())).thenReturn(Optional.of(sampleStaff(UUID.randomUUID())));

            assertThatThrownBy(() -> staffService.createStaff(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("CPF já cadastrado")
                    .message().doesNotContain(request.getCpf());

            verify(staffPortOut, never()).save(any());
        }

        @Test
        void rejectsDuplicateEmail() {
            var request = sampleRequest();
            when(staffPortOut.findByCpf(request.getCpf())).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleStaff(UUID.randomUUID())));

            assertThatThrownBy(() -> staffService.createStaff(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Email já cadastrado")
                    .message().doesNotContain(request.getEmail());

            verify(staffPortOut, never()).save(any());
        }
    }

    @Nested
    class GetAllStaff {

        private final PageRequest pageable = PageRequest.of(0, 20);

        @Test
        void delegatesToPortOutEnabledOnlyQuery() {
            var staffList = List.of(sampleStaff(UUID.randomUUID()), sampleStaff(UUID.randomUUID()));
            when(staffPortOut.findAllByEnabledTrue(pageable)).thenReturn(new PageImpl<>(staffList));

            var result = staffService.getAllStaff(pageable);

            assertThat(result.getContent()).isEqualTo(staffList);
        }

        @Test
        void returnsEmptyListWhenNoneRegistered() {
            when(staffPortOut.findAllByEnabledTrue(pageable)).thenReturn(new PageImpl<>(List.of()));

            assertThat(staffService.getAllStaff(pageable).getContent()).isEmpty();
        }
    }

    @Nested
    class GetStaffById {

        @Test
        void returnsStaffWhenFound() {
            var id = UUID.randomUUID();
            var staff = sampleStaff(id);
            when(staffPortOut.findById(id)).thenReturn(Optional.of(staff));

            assertThat(staffService.getStaffById(id)).isEqualTo(staff);
        }

        @Test
        void throwsNotFoundWhenMissing() {
            var id = UUID.randomUUID();
            when(staffPortOut.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> staffService.getStaffById(id))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class GetStaffByIdOrCpf {

        @Test
        void findsByIdWhenValueIsAValidUuid() {
            var id = UUID.randomUUID();
            var staff = sampleStaff(id);
            when(staffPortOut.findById(id)).thenReturn(Optional.of(staff));

            assertThat(staffService.getStaffByIdOrCpf(id.toString())).isEqualTo(staff);
        }

        @Test
        void fallsBackToCpfWhenValueIsNotAUuid() {
            var staff = sampleStaff(UUID.randomUUID());
            when(staffPortOut.findByCpf("12345678900")).thenReturn(Optional.of(staff));

            assertThat(staffService.getStaffByIdOrCpf("12345678900")).isEqualTo(staff);
        }

        @Test
        void fallsBackToCpfWhenValidUuidIsNotFound() {
            var id = UUID.randomUUID();
            var staff = sampleStaff(UUID.randomUUID());
            when(staffPortOut.findById(id)).thenReturn(Optional.empty());
            when(staffPortOut.findByCpf(id.toString())).thenReturn(Optional.of(staff));

            assertThat(staffService.getStaffByIdOrCpf(id.toString())).isEqualTo(staff);
        }

        @Test
        void throwsNotFoundWhenNeitherIdNorCpfMatch() {
            when(staffPortOut.findByCpf("00000000000")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> staffService.getStaffByIdOrCpf("00000000000"))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class UpdateStaff {

        @Test
        void updatesMutableFieldsAndSaves() {
            var id = UUID.randomUUID();
            var staff = sampleStaff(id);
            when(staffPortOut.findById(id)).thenReturn(Optional.of(staff));
            when(staffPortOut.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var request = new StaffRequestDto();
            request.setName("João Atualizado");
            request.setEmail("novo@petshop.com");
            request.setPhone("11888888888");
            request.setRole(Role.ADMIN);

            var result = staffService.updateStaff(id, request);

            assertThat(result.getName()).isEqualTo("João Atualizado");
            assertThat(result.getEmail()).isEqualTo("novo@petshop.com");
            assertThat(result.getPhone()).isEqualTo("11888888888");
            assertThat(result.getRole()).isEqualTo(Role.ADMIN);
            assertThat(result.getCpf()).isEqualTo("12345678900");
        }

        @Test
        void throwsNotFoundWhenStaffDoesNotExist() {
            var id = UUID.randomUUID();
            when(staffPortOut.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> staffService.updateStaff(id, sampleRequest()))
                    .isInstanceOf(NotFoundException.class);

            verify(staffPortOut, never()).save(any());
        }
    }

    @Nested
    class DeleteStaff {

        @Test
        void disablesStaffInsteadOfRemoving() {
            var id = UUID.randomUUID();
            var staff = sampleStaff(id);
            when(staffPortOut.findById(id)).thenReturn(Optional.of(staff));
            when(staffPortOut.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var result = staffService.deleteStaff(id);

            assertThat(result.getEnabled()).isFalse();
            verify(staffPortOut).save(staff);
        }

        @Test
        void throwsNotFoundWhenStaffDoesNotExist() {
            var id = UUID.randomUUID();
            when(staffPortOut.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> staffService.deleteStaff(id))
                    .isInstanceOf(NotFoundException.class);

            verify(staffPortOut, never()).save(any());
        }
    }

    @Nested
    class ReactivateStaff {

        @Test
        void enablesPreviouslyDisabledStaff() {
            var id = UUID.randomUUID();
            var staff = sampleStaff(id);
            staff.setEnabled(false);
            when(staffPortOut.findById(id)).thenReturn(Optional.of(staff));
            when(staffPortOut.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var result = staffService.reactivateStaff(id);

            assertThat(result.getEnabled()).isTrue();
            verify(staffPortOut).save(staff);
        }

        @Test
        void isIdempotentWhenStaffIsAlreadyEnabled() {
            var id = UUID.randomUUID();
            var staff = sampleStaff(id);
            when(staffPortOut.findById(id)).thenReturn(Optional.of(staff));
            when(staffPortOut.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var result = staffService.reactivateStaff(id);

            assertThat(result.getEnabled()).isTrue();
        }

        @Test
        void throwsNotFoundWhenStaffDoesNotExist() {
            var id = UUID.randomUUID();
            when(staffPortOut.findById(id)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> staffService.reactivateStaff(id))
                    .isInstanceOf(NotFoundException.class);

            verify(staffPortOut, never()).save(any());
        }
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clear();
        }
    }

    private void givenNewStaffCanBeCreated(StaffRequestDto request) {
        given(staffPortOut.findByCpf(request.getCpf())).willReturn(Optional.empty());
        given(staffPortOut.findByEmail(request.getEmail())).willReturn(Optional.empty());
        given(staffPortOut.save(any(Staff.class))).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("Deve criar o usuário no IdP sem senha e enviar o e-mail de senha quando cadastrar funcionário")
    void shouldCreateIdentityUserWithoutPasswordAndSendSetPasswordEmailWhenCreatingStaff() {
        var request = sampleRequest();
        givenNewStaffCanBeCreated(request);
        given(identityProviderPortOut.createUser(any(IdentityUser.class), org.mockito.ArgumentMatchers.isNull()))
                .willReturn("kc-1");
        TransactionSynchronizationManager.initSynchronization();

        var result = staffService.createStaff(request);

        var captor = ArgumentCaptor.forClass(IdentityUser.class);
        then(identityProviderPortOut).should().createUser(captor.capture(), org.mockito.ArgumentMatchers.isNull());
        assertThat(captor.getValue().userId()).isEqualTo(result.getId());
        assertThat(captor.getValue().role()).isEqualTo(Role.RECEPTIONIST);
        then(identityProviderPortOut).should().sendSetPasswordEmail("kc-1");
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    @DisplayName("Deve manter o cadastro e apenas logar aviso quando o envio do e-mail de senha falhar")
    void shouldKeepStaffAndOnlyLogWarningWhenSetPasswordEmailFails() {
        var request = sampleRequest();
        givenNewStaffCanBeCreated(request);
        given(identityProviderPortOut.createUser(any(IdentityUser.class), org.mockito.ArgumentMatchers.isNull()))
                .willReturn("kc-1");
        willThrow(new IllegalStateException("smtp fora")).given(identityProviderPortOut).sendSetPasswordEmail("kc-1");

        try (var logs = LogCaptor.of(StaffService.class)) {
            var result = staffService.createStaff(request);

            assertThat(result.getEnabled()).isTrue();
            assertThat(logs.has(Level.WARN, "Set-password email not sent")).isTrue();
        }
        then(staffPortOut).should().save(any(Staff.class));
    }

    @Test
    @DisplayName("Deve sincronizar o usuário no IdP com os dados salvos quando atualizar o funcionário")
    void shouldSyncIdentityUserWithSavedDataWhenUpdatingStaff() {
        var id = UUID.randomUUID();
        given(staffPortOut.findById(id)).willReturn(Optional.of(sampleStaff(id)));
        given(staffPortOut.save(any(Staff.class))).willAnswer(invocation -> invocation.getArgument(0));
        var request = new StaffRequestDto();
        request.setName("João Atualizado");
        request.setRole(Role.ADMIN);

        staffService.updateStaff(id, request);

        var captor = ArgumentCaptor.forClass(IdentityUser.class);
        then(identityProviderPortOut).should().updateUser(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(id);
        assertThat(captor.getValue().name()).isEqualTo("João Atualizado");
        assertThat(captor.getValue().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("Deve desabilitar o usuário no IdP quando desativar o funcionário")
    void shouldDisableIdentityUserWhenDeletingStaff() {
        var id = UUID.randomUUID();
        given(staffPortOut.findById(id)).willReturn(Optional.of(sampleStaff(id)));
        given(staffPortOut.save(any(Staff.class))).willAnswer(invocation -> invocation.getArgument(0));

        staffService.deleteStaff(id);

        then(identityProviderPortOut).should().setEnabled(id, false);
    }

    @Test
    @DisplayName("Deve reabilitar o usuário no IdP quando reativar o funcionário")
    void shouldEnableIdentityUserWhenReactivatingStaff() {
        var id = UUID.randomUUID();
        var staff = sampleStaff(id);
        staff.setEnabled(false);
        given(staffPortOut.findById(id)).willReturn(Optional.of(staff));
        given(staffPortOut.save(any(Staff.class))).willAnswer(invocation -> invocation.getArgument(0));

        staffService.reactivateStaff(id);

        then(identityProviderPortOut).should().setEnabled(id, true);
    }
}
