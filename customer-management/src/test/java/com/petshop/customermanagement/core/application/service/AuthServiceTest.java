package com.petshop.customermanagement.core.application.service;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.in.dto.ChangePasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.ForgotPasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.LoginRequestDto;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Login/esqueci-senha/troca-senha únicos pra Customer e Staff (ver
 * AuthService) — cobre os dois caminhos (achou como cliente, achou como
 * funcionário, não achou em nenhum) em cada um dos três fluxos.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CustomerPortOut customerPortOut;

    @Mock
    private StaffPortOut staffPortOut;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private NotificationPortOut notificationPortOut;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(customerPortOut, staffPortOut, passwordEncoder, jwtService, notificationPortOut);
    }

    private Customer sampleCustomer(String passwordHash) {
        var customer = new Customer("Maria", "12345678900", "maria@mail.com", "11999999999");
        customer.setPet(new ArrayList<>());
        customer.setPasswordHash(passwordHash);
        customer.setMustChangePassword(true);
        return customer;
    }

    private Staff sampleStaff(String passwordHash) {
        var staff = new Staff(UUID.randomUUID(), "Joao", "98765432100", "joao@petshop.local", "11988887777", true, Role.RECEPTIONIST);
        staff.setPasswordHash(passwordHash);
        staff.setMustChangePassword(false);
        return staff;
    }

    @Nested
    class Login {

        @Test
        void authenticatesAsCustomerWhenEmailBelongsToCustomer() {
            var customer = sampleCustomer("hashed-password");
            var request = new LoginRequestDto(customer.getEmail(), "raw-password");
            when(customerPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
            when(passwordEncoder.matches("raw-password", "hashed-password")).thenReturn(true);
            when(jwtService.generateToken(customer.getId(), customer.getEmail(), customer.getName(),
                    customer.getCpf(), customer.getPhone(), Role.CUSTOMER, AccountType.CUSTOMER)).thenReturn("token-customer");
            when(jwtService.expirationSeconds()).thenReturn(3600L);

            var response = authService.login(request);

            assertThat(response.accessToken()).isEqualTo("token-customer");
            assertThat(response.tokenType()).isEqualTo("Bearer");
            assertThat(response.expiresIn()).isEqualTo(3600L);
            assertThat(response.role()).isEqualTo(Role.CUSTOMER);
            assertThat(response.type()).isEqualTo(AccountType.CUSTOMER);
            assertThat(response.mustChangePassword()).isTrue();
            verifyNoInteractions(staffPortOut);
            verify(customerPortOut, never()).save(any());
        }

        @Test
        void reactivatesDisabledCustomerOnSuccessfulLogin() {
            var customer = sampleCustomer("hashed-password");
            customer.setEnabled(false);
            var request = new LoginRequestDto(customer.getEmail(), "raw-password");
            when(customerPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
            when(passwordEncoder.matches("raw-password", "hashed-password")).thenReturn(true);
            when(jwtService.generateToken(customer.getId(), customer.getEmail(), customer.getName(),
                    customer.getCpf(), customer.getPhone(), Role.CUSTOMER, AccountType.CUSTOMER)).thenReturn("token-customer");
            when(jwtService.expirationSeconds()).thenReturn(3600L);
            when(customerPortOut.save(customer)).thenReturn(customer);

            authService.login(request);

            assertThat(customer.getEnabled()).isTrue();
            verify(customerPortOut).save(customer);
        }

        @Test
        void doesNotReactivateWhenCustomerPasswordDoesNotMatch() {
            var customer = sampleCustomer("hashed-password");
            customer.setEnabled(false);
            var request = new LoginRequestDto(customer.getEmail(), "wrong-password");
            when(customerPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
            when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);

            assertThat(customer.getEnabled()).isFalse();
            verify(customerPortOut, never()).save(any());
        }

        @Test
        void throwsBadCredentialsWhenCustomerPasswordDoesNotMatch() {
            var customer = sampleCustomer("hashed-password");
            var request = new LoginRequestDto(customer.getEmail(), "wrong-password");
            when(customerPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
            when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);

            verifyNoInteractions(jwtService);
        }

        @Test
        void throwsBadCredentialsWhenCustomerHasNoPasswordHashYet() {
            var customer = sampleCustomer(null);
            var request = new LoginRequestDto(customer.getEmail(), "anything");
            when(customerPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
        }

        @Test
        void fallsBackToStaffWhenNoCustomerHasThatEmail() {
            var staff = sampleStaff("hashed-staff-password");
            var request = new LoginRequestDto(staff.getEmail(), "raw-password");
            when(customerPortOut.findByEmail(staff.getEmail())).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));
            when(passwordEncoder.matches("raw-password", "hashed-staff-password")).thenReturn(true);
            when(jwtService.generateToken(staff.getId(), staff.getEmail(), staff.getName(),
                    staff.getCpf(), staff.getPhone(), staff.getRole(), AccountType.STAFF)).thenReturn("token-staff");
            when(jwtService.expirationSeconds()).thenReturn(3600L);

            var response = authService.login(request);

            assertThat(response.accessToken()).isEqualTo("token-staff");
            assertThat(response.role()).isEqualTo(Role.RECEPTIONIST);
            assertThat(response.type()).isEqualTo(AccountType.STAFF);
            assertThat(response.mustChangePassword()).isFalse();
        }

        @Test
        void throwsBadCredentialsWhenStaffPasswordDoesNotMatch() {
            var staff = sampleStaff("hashed-staff-password");
            var request = new LoginRequestDto(staff.getEmail(), "wrong-password");
            when(customerPortOut.findByEmail(staff.getEmail())).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));
            when(passwordEncoder.matches("wrong-password", "hashed-staff-password")).thenReturn(false);

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);
        }

        @Test
        void throwsBadCredentialsWhenEmailBelongsToNeitherCustomerNorStaff() {
            var request = new LoginRequestDto("ninguem@mail.com", "raw-password");
            when(customerPortOut.findByEmail("ninguem@mail.com")).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail("ninguem@mail.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(request)).isInstanceOf(BadCredentialsException.class);

            verifyNoInteractions(jwtService);
        }
    }

    @Nested
    class ForgotPassword {

        @Test
        void resetsCustomerPasswordAndPublishesEmailWhenEmailAndCpfMatch() {
            var customer = sampleCustomer("old-hash");
            var request = new ForgotPasswordRequestDto(customer.getEmail(), customer.getCpf());
            when(customerPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
            when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.forgotPassword(request);

            assertThat(customer.getPasswordHash()).isEqualTo("new-hash");
            assertThat(customer.getMustChangePassword()).isTrue();
            verify(customerPortOut).save(customer);
            verify(notificationPortOut).sendPasswordReset(eq(customer.getEmail()), anyString(), anyString());
            verifyNoInteractions(staffPortOut);
        }

        @Test
        void doesNothingWhenCustomerEmailMatchesButCpfDoesNot() {
            var customer = sampleCustomer("old-hash");
            var request = new ForgotPasswordRequestDto(customer.getEmail(), "00000000000");
            when(customerPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
            when(staffPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.empty());

            authService.forgotPassword(request);

            verify(customerPortOut, never()).save(any());
            verifyNoInteractions(notificationPortOut);
        }

        @Test
        void resetsStaffPasswordWhenNoCustomerHasThatEmailButStaffDoes() {
            var staff = sampleStaff("old-hash");
            var request = new ForgotPasswordRequestDto(staff.getEmail(), staff.getCpf());
            when(customerPortOut.findByEmail(staff.getEmail())).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));
            when(passwordEncoder.encode(anyString())).thenReturn("new-hash");
            when(staffPortOut.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.forgotPassword(request);

            assertThat(staff.getPasswordHash()).isEqualTo("new-hash");
            assertThat(staff.getMustChangePassword()).isTrue();
            verify(staffPortOut).save(staff);
            verify(notificationPortOut).sendPasswordReset(eq(staff.getEmail()), anyString(), anyString());
        }

        @Test
        void doesNothingWhenEmailMatchesNeitherCustomerNorStaff() {
            var request = new ForgotPasswordRequestDto("ninguem@mail.com", "12345678900");
            when(customerPortOut.findByEmail("ninguem@mail.com")).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail("ninguem@mail.com")).thenReturn(Optional.empty());

            authService.forgotPassword(request);

            verifyNoInteractions(notificationPortOut);
        }
    }

    @Nested
    class ChangePassword {

        @Test
        void changesCustomerPasswordWhenCurrentPasswordMatches() {
            var customer = sampleCustomer("old-hash");
            var request = new ChangePasswordRequestDto(customer.getEmail(), "old-password", "new-password");
            when(customerPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
            when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
            when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
            when(customerPortOut.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.changePassword(request);

            assertThat(customer.getPasswordHash()).isEqualTo("new-hash");
            assertThat(customer.getMustChangePassword()).isFalse();
            verify(customerPortOut).save(customer);
            verifyNoInteractions(staffPortOut);
        }

        @Test
        void throwsBadCredentialsWhenCustomerCurrentPasswordIsWrong() {
            var customer = sampleCustomer("old-hash");
            var request = new ChangePasswordRequestDto(customer.getEmail(), "wrong-password", "new-password");
            when(customerPortOut.findByEmail(customer.getEmail())).thenReturn(Optional.of(customer));
            when(passwordEncoder.matches("wrong-password", "old-hash")).thenReturn(false);

            assertThatThrownBy(() -> authService.changePassword(request)).isInstanceOf(BadCredentialsException.class);

            verify(customerPortOut, never()).save(any());
        }

        @Test
        void changesStaffPasswordWhenNoCustomerHasThatEmail() {
            var staff = sampleStaff("old-hash");
            var request = new ChangePasswordRequestDto(staff.getEmail(), "old-password", "new-password");
            when(customerPortOut.findByEmail(staff.getEmail())).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail(staff.getEmail())).thenReturn(Optional.of(staff));
            when(passwordEncoder.matches("old-password", "old-hash")).thenReturn(true);
            when(passwordEncoder.encode("new-password")).thenReturn("new-hash");
            when(staffPortOut.save(any(Staff.class))).thenAnswer(inv -> inv.getArgument(0));

            authService.changePassword(request);

            assertThat(staff.getPasswordHash()).isEqualTo("new-hash");
            assertThat(staff.getMustChangePassword()).isFalse();
            verify(staffPortOut).save(staff);
        }

        @Test
        void throwsBadCredentialsWhenNeitherCustomerNorStaffHasThatEmail() {
            var request = new ChangePasswordRequestDto("ninguem@mail.com", "old-password", "new-password");
            when(customerPortOut.findByEmail("ninguem@mail.com")).thenReturn(Optional.empty());
            when(staffPortOut.findByEmail("ninguem@mail.com")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.changePassword(request)).isInstanceOf(BadCredentialsException.class);
        }
    }
}
