package com.petshop.customermanagement.core.application.service;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.customermanagement.api.rest.dto.LoginResponseDto;
import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.domain.Staff;
import com.petshop.customermanagement.core.port.in.AuthPortIn;
import com.petshop.customermanagement.core.port.in.dto.ChangePasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.ForgotPasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.LoginRequestDto;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import com.petshop.customermanagement.core.port.out.NotificationPortOut;
import com.petshop.customermanagement.core.port.out.StaffPortOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Login/esqueci-senha/troca-senha únicos pra Customer e Staff — o email é
 * o identificador em ambas as tabelas, então o fluxo tenta Customer
 * primeiro e cai pra Staff se não achar (não há sobreposição de email
 * entre as duas, cada uma tem sua própria constraint unique).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService implements AuthPortIn {

    // Sem caracteres ambíguos (0/O, 1/l/I) pra senha gerada não confundir
    // quem for digitar.
    private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int GENERATED_PASSWORD_LENGTH = 10;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID_CREDENTIALS_MESSAGE = "Email ou senha inválidos.";

    private final CustomerPortOut customerPortOut;
    private final StaffPortOut staffPortOut;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final NotificationPortOut notificationPortOut;

    @Override
    public LoginResponseDto login(LoginRequestDto request) {
        var customer = customerPortOut.findByEmail(request.getEmail());
        if (customer.isPresent()) {
            return loginAsCustomer(customer.get(), request.getPassword());
        }

        var staff = staffPortOut.findByEmail(request.getEmail());
        if (staff.isPresent()) {
            return loginAsStaff(staff.get(), request.getPassword());
        }

        throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
    }

    private LoginResponseDto loginAsCustomer(Customer customer, String rawPassword) {
        checkPassword(customer.getPasswordHash(), rawPassword);
        reactivateIfDisabled(customer);
        var token = jwtService.generateToken(customer.getId(), customer.getEmail(), customer.getName(),
                customer.getCpf(), customer.getPhone(), Role.CUSTOMER, AccountType.CUSTOMER);
        log.info("Login successful for customer: {}", customer.getEmail());
        return new LoginResponseDto(token, jwtService.expirationSeconds(), Role.CUSTOMER, AccountType.CUSTOMER,
                Boolean.TRUE.equals(customer.getMustChangePassword()));
    }

    // Cliente desativado (delete = soft-delete, ver CustomerService) que
    // consegue logar de novo (senha continua válida) reativa a própria
    // conta automaticamente — diferente do Staff, cuja reativação é uma
    // ação manual do ADMIN (ver StaffService.reactivateStaff).
    private void reactivateIfDisabled(Customer customer) {
        if (!Boolean.TRUE.equals(customer.getEnabled())) {
            customer.setEnabled(true);
            customerPortOut.save(customer);
            log.info("Customer account reactivated on login: {}", customer.getEmail());
        }
    }

    private LoginResponseDto loginAsStaff(Staff staff, String rawPassword) {
        checkPassword(staff.getPasswordHash(), rawPassword);
        var token = jwtService.generateToken(staff.getId(), staff.getEmail(), staff.getName(),
                staff.getCpf(), staff.getPhone(), staff.getRole(), AccountType.STAFF);
        log.info("Login successful for staff: {}", staff.getEmail());
        return new LoginResponseDto(token, jwtService.expirationSeconds(), staff.getRole(), AccountType.STAFF,
                Boolean.TRUE.equals(staff.getMustChangePassword()));
    }

    private void checkPassword(String passwordHash, String rawPassword) {
        if (passwordHash == null || !passwordEncoder.matches(rawPassword, passwordHash)) {
            throw new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
    }

    @Override
    public void forgotPassword(ForgotPasswordRequestDto request) {
        var customer = customerPortOut.findByEmail(request.getEmail())
                .filter(c -> c.getCpf().equals(request.getCpf()));
        if (customer.isPresent()) {
            resetCustomerPasswordAndNotify(customer.get());
            return;
        }

        staffPortOut.findByEmail(request.getEmail())
                .filter(s -> s.getCpf().equals(request.getCpf()))
                .ifPresent(this::resetStaffPasswordAndNotify);
        // Se nada bateu, não faz nada — resposta 200 genérica de qualquer
        // forma (ver AuthControllerImpl), pra não revelar se o email existe.
    }

    private void resetCustomerPasswordAndNotify(Customer customer) {
        var newPassword = generateRandomPassword();
        customer.setPasswordHash(passwordEncoder.encode(newPassword));
        customer.setMustChangePassword(true);
        customerPortOut.save(customer);
        notificationPortOut.sendPasswordReset(customer.getEmail(), customer.getName(), newPassword);
        log.info("Password reset for customer: {}", customer.getEmail());
    }

    private void resetStaffPasswordAndNotify(Staff staff) {
        var newPassword = generateRandomPassword();
        staff.setPasswordHash(passwordEncoder.encode(newPassword));
        staff.setMustChangePassword(true);
        staffPortOut.save(staff);
        notificationPortOut.sendPasswordReset(staff.getEmail(), staff.getName(), newPassword);
        log.info("Password reset for staff: {}", staff.getEmail());
    }

    @Override
    public void changePassword(ChangePasswordRequestDto request) {
        var customer = customerPortOut.findByEmail(request.getEmail());
        if (customer.isPresent()) {
            changeCustomerPassword(customer.get(), request);
            return;
        }

        var staff = staffPortOut.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
        changeStaffPassword(staff, request);
    }

    private void changeCustomerPassword(Customer customer, ChangePasswordRequestDto request) {
        checkCurrentPassword(customer.getPasswordHash(), request.getCurrentPassword());
        customer.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        customer.setMustChangePassword(false);
        customerPortOut.save(customer);
        log.info("Password changed for customer: {}", customer.getEmail());
    }

    private void changeStaffPassword(Staff staff, ChangePasswordRequestDto request) {
        checkCurrentPassword(staff.getPasswordHash(), request.getCurrentPassword());
        staff.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        staff.setMustChangePassword(false);
        staffPortOut.save(staff);
        log.info("Password changed for staff: {}", staff.getEmail());
    }

    private void checkCurrentPassword(String passwordHash, String currentPassword) {
        if (passwordHash == null || !passwordEncoder.matches(currentPassword, passwordHash)) {
            throw new BadCredentialsException("Senha atual incorreta.");
        }
    }

    private String generateRandomPassword() {
        var sb = new StringBuilder(GENERATED_PASSWORD_LENGTH);
        for (int i = 0; i < GENERATED_PASSWORD_LENGTH; i++) {
            sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
