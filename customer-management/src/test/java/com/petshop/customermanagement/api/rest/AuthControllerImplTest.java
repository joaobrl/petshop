package com.petshop.customermanagement.api.rest;

import com.petshop.customermanagement.core.port.in.EmailVerificationPortIn;
import com.petshop.customermanagement.core.port.in.dto.ResendVerificationRequestDto;
import com.petshop.customermanagement.core.port.in.dto.VerifyEmailRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthControllerImplTest {

    @Mock
    private EmailVerificationPortIn emailVerificationPortIn;

    private AuthControllerImpl authController;

    @BeforeEach
    void setUp() {
        authController = new AuthControllerImpl(emailVerificationPortIn);
    }

    @Nested
    class VerifyEmail {

        @Test
        @DisplayName("Deve retornar 204 e delegar o token quando verificar e-mail")
        void shouldReturnNoContentAndDelegateWhenVerifyingEmail() {
            var request = new VerifyEmailRequestDto("token-abc");

            var result = authController.verifyEmail(request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
            assertThat(result.getBody()).isNull();
            verify(emailVerificationPortIn).verifyEmail("token-abc");
        }
    }

    @Nested
    class ResendVerification {

        @Test
        @DisplayName("Deve retornar 202 e delegar o e-mail quando reenviar verificação")
        void shouldReturnAcceptedAndDelegateWhenResendingVerification() {
            var request = new ResendVerificationRequestDto("joao@mail.com");

            var result = authController.resendVerification(request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(result.getBody()).isNull();
            verify(emailVerificationPortIn).resendVerification("joao@mail.com");
        }

        @Test
        @DisplayName("Deve retornar a mesma resposta 202 quando o e-mail não existir")
        void shouldReturnSameAcceptedResponseWhenEmailDoesNotExist() {
            var request = new ResendVerificationRequestDto("ninguem@mail.com");

            var result = authController.resendVerification(request);

            assertThat(result.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
            assertThat(result.getBody()).isNull();
            verify(emailVerificationPortIn).resendVerification("ninguem@mail.com");
        }
    }
}
