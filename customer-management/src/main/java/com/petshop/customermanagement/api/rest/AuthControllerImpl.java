package com.petshop.customermanagement.api.rest;

import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.customermanagement.api.rest.dto.LoginResponseDto;
import com.petshop.customermanagement.core.port.in.AuthPortIn;
import com.petshop.customermanagement.core.port.in.EmailVerificationPortIn;
import com.petshop.customermanagement.core.port.in.dto.ChangePasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.ForgotPasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.LoginRequestDto;
import com.petshop.customermanagement.core.port.in.dto.ResendVerificationRequestDto;
import com.petshop.customermanagement.core.port.in.dto.VerifyEmailRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthControllerImpl implements AuthController {

    private final AuthPortIn authPortIn;
    private final EmailVerificationPortIn emailVerificationPortIn;

    @Override
    public ResponseEntity<LoginResponseDto> login(LoginRequestDto request) {
        return ResponseEntity.ok(authPortIn.login(request));
    }

    @Override
    public ResponseEntity<Void> forgotPassword(ForgotPasswordRequestDto request) {
        authPortIn.forgotPassword(request);
        // Sempre 200, exista ou não o email — ver AuthPortIn.forgotPassword.
        return ResponseEntity.ok().build();
    }

    @Override
    public ResponseEntity<Void> changePassword(ChangePasswordRequestDto request, AuthenticatedUser user) {
        if (user == null || !user.email().equalsIgnoreCase(request.getEmail())) {
            throw new AccessDeniedException("Você só pode trocar a própria senha.");
        }
        authPortIn.changePassword(request);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> verifyEmail(VerifyEmailRequestDto request) {
        emailVerificationPortIn.verifyEmail(request.getToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resendVerification(ResendVerificationRequestDto request) {
        emailVerificationPortIn.resendVerification(request.getEmail());
        // Sempre 202, exista ou não o email, verificado ou não (RF4) — ver
        // EmailVerificationPortIn.resendVerification.
        return ResponseEntity.accepted().build();
    }
}
