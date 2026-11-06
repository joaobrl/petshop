package com.petshop.customermanagement.api.rest;

import com.petshop.customermanagement.core.port.in.EmailVerificationPortIn;
import com.petshop.customermanagement.core.port.in.dto.ResendVerificationRequestDto;
import com.petshop.customermanagement.core.port.in.dto.VerifyEmailRequestDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthControllerImpl implements AuthController {

    private final EmailVerificationPortIn emailVerificationPortIn;

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
