package com.petshop.customermanagement.api.rest;

import com.petshop.customermanagement.core.port.in.dto.ResendVerificationRequestDto;
import com.petshop.customermanagement.core.port.in.dto.VerifyEmailRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Só a verificação de e-mail (SDD-020). Login, troca e recuperação de senha são do
 * Keycloak desde a SDD-024 F3 — o customer-management não guarda nem valida senha.
 */
@RequestMapping("/api/v1/auth")
public interface AuthController {

    @PostMapping("/verify-email")
    ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequestDto request);

    @PostMapping("/resend-verification")
    ResponseEntity<Void> resendVerification(@Valid @RequestBody ResendVerificationRequestDto request);
}
