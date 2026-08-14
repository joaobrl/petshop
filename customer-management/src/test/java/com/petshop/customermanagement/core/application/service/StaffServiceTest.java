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
import org.springframework.security.crypto.password.PasswordEncoder;

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
    private PasswordEncoder passwordEncoder;

    private StaffService staffService;

    @BeforeEach
    void setUp() {
        staffService = new StaffService(staffPortOut, passwordEncoder);
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
        void savesStaffWithHashedCpfAsInitialPassword() {
            var request = sampleRequest();
            when(staffPortOut.findByCpf(request.getCpf())).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail(request.getEmail())).thenReturn(Optional.empty());
            when(passwordEncoder.encode(request.getCpf())).thenReturn("hashed-cpf");
            when(staffPortOut.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

            var result = staffService.createStaff(request);

            assertThat(result.getName()).isEqualTo("João");
            assertThat(result.getCpf()).isEqualTo("12345678900");
            assertThat(result.getPasswordHash()).isEqualTo("hashed-cpf");
            assertThat(result.getMustChangePassword()).isTrue();
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
                    .hasMessageContaining(request.getCpf());

            verify(staffPortOut, never()).save(any());
        }

        @Test
        void rejectsDuplicateEmail() {
            var request = sampleRequest();
            when(staffPortOut.findByCpf(request.getCpf())).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail(request.getEmail())).thenReturn(Optional.of(sampleStaff(UUID.randomUUID())));

            assertThatThrownBy(() -> staffService.createStaff(request))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(request.getEmail());

            verify(staffPortOut, never()).save(any());
        }
    }

    @Nested
    class GetAllStaff {

        @Test
        void returnsOnlyEnabledStaffFromPort() {
            var staffList = List.of(sampleStaff(UUID.randomUUID()), sampleStaff(UUID.randomUUID()));
            when(staffPortOut.findAll()).thenReturn(staffList);

            var result = staffService.getAllStaff();

            assertThat(result).isEqualTo(staffList);
        }

        @Test
        void excludesDisabledStaff() {
            var active = sampleStaff(UUID.randomUUID());
            var inactive = sampleStaff(UUID.randomUUID());
            inactive.setEnabled(false);
            when(staffPortOut.findAll()).thenReturn(List.of(active, inactive));

            var result = staffService.getAllStaff();

            assertThat(result).containsExactly(active);
        }

        @Test
        void returnsEmptyListWhenNoneRegistered() {
            when(staffPortOut.findAll()).thenReturn(List.of());

            assertThat(staffService.getAllStaff()).isEmpty();
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
}
