package com.petshop.customermanagement.api.rest;

import com.petshop.customermanagement.api.rest.dto.LoginResponseDto;
import com.petshop.customermanagement.core.port.in.dto.ChangePasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.ForgotPasswordRequestDto;
import com.petshop.customermanagement.core.port.in.dto.LoginRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import com.petshop.commons.security.jwt.AuthenticatedUser;

@RequestMapping("/api/v1/auth")
public interface AuthController {

    @PostMapping("/login")
    ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request);

    @PostMapping("/forgot-password")
    ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequestDto request);

    @PatchMapping("/change-password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequestDto request, @AuthenticationPrincipal AuthenticatedUser user);
}
