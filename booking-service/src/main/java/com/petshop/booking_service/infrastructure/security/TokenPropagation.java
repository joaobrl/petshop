package com.petshop.booking_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A única saída Feign do booking-service é o staff-service
 * ({@code /api/v1/availability/**}), que é uma rota interna — nunca deve
 * aceitar o token de um cliente diretamente. Por isso, diferente do
 * order-service, aqui não existe relay: sempre assinamos um token de
 * serviço novo antes de chamar, mesmo padrão que o staff-service já usa
 * pra chamar o customer-management.
 */
@Component
@RequiredArgsConstructor
public class TokenPropagation {

    private static final String SERVICE_NAME = "booking-service";

    private final JwtService jwtService;

    public String mintServiceToken() {
        return "Bearer " + jwtService.generateServiceToken(SERVICE_NAME);
    }
}
