package com.petshop.customermanagement.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.customermanagement.api.rest.dto.LoginResponseDto;
import com.petshop.customermanagement.core.port.in.AuthPortIn;
import com.petshop.customermanagement.core.port.in.dto.ChangePasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.ForgotPasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.LoginRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerImplTest {

    private static final JwtService JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Mock
    private AuthPortIn authPortIn;

    private AuthControllerImpl authController;

    @BeforeEach
    void setUp() {
        authController = new AuthControllerImpl(authPortIn, JWT_SERVICE);
    }

    private AuthenticatedUser userWithEmail(String email) {
        return new AuthenticatedUser(UUID.randomUUID(), email, "João", "12345678900", "11999999999",
                Role.CUSTOMER, AccountType.CUSTOMER);
    }

    private String tokenFor(AuthenticatedUser user) {
        return "Bearer " + JWT_SERVICE.generateToken(user.id(), user.email(), user.name(), user.cpf(), user.phone(), user.role(), user.type());
    }

    @Nested
    class Login {

        @Test
        void delegatesToAuthPortInAndReturnsOk() {
            var request = new LoginRequestDto("joao@mail.com", "12345678900");
            var response = new LoginResponseDto("token", 3600L, Role.CUSTOMER, AccountType.CUSTOMER, true);
            when(authPortIn.login(request)).thenReturn(response);

            var result = authController.login(request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(result.getBody()).isEqualTo(response);
        }
    }

    @Nested
    class ForgotPassword {

        @Test
        void alwaysReturnsOkRegardlessOfOutcome() {
            var request = new ForgotPasswordRequestDto("joao@mail.com", "12345678900");

            var result = authController.forgotPassword(request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(authPortIn).forgotPassword(request);
        }
    }

    @Nested
    class ChangePassword {

        @Test
        void delegatesWhenUserOwnsTheEmail() {
            var request = new ChangePasswordRequestDto("joao@mail.com", "12345678900", "novaSenha123");
            var user = userWithEmail("joao@mail.com");

            var result = authController.changePassword(request, tokenFor(user));

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(authPortIn).changePassword(request);
        }

        @Test
        void delegatesWhenEmailMatchesCaseInsensitively() {
            var request = new ChangePasswordRequestDto("Joao@Mail.com", "12345678900", "novaSenha123");
            var user = userWithEmail("joao@mail.com");

            var result = authController.changePassword(request, tokenFor(user));

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            verify(authPortIn).changePassword(request);
        }

        @Test
        void throwsAccessDeniedWhenUserIsNull() {
            var request = new ChangePasswordRequestDto("joao@mail.com", "12345678900", "novaSenha123");

            assertThatThrownBy(() -> authController.changePassword(request, null))
                    .isInstanceOf(AccessDeniedException.class);

            verify(authPortIn, never()).changePassword(request);
        }

        @Test
        void throwsAccessDeniedWhenEmailDoesNotMatch() {
            var request = new ChangePasswordRequestDto("outro@mail.com", "12345678900", "novaSenha123");
            var user = userWithEmail("joao@mail.com");

            assertThatThrownBy(() -> authController.changePassword(request, tokenFor(user)))
                    .isInstanceOf(AccessDeniedException.class);

            verify(authPortIn, never()).changePassword(request);
        }
    }
}
