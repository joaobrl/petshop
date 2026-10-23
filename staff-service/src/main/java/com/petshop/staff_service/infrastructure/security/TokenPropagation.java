package com.petshop.staff_service.infrastructure.security;

import com.petshop.commons.security.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A única chamada Feign deste serviço ({@code StaffRegistryFeign}, pro
 * customer-management) é uma consulta de sistema — "quem é staff e qual o
 * cargo" — não uma ação em nome de um usuário específico. Por isso, ao
 * contrário do booking-service/order-service (que fazem token relay), aqui
 * é sempre token de serviço: repassar o token de quem originou o pedido
 * faria a consulta cair em {@code GET /api/v1/staff/all}, que exige ADMIN —
 * e um cliente nunca tem essa role.
 */
@Component
@RequiredArgsConstructor
public class TokenPropagation {

    private static final String SERVICE_NAME = "staff-service";

    private final JwtService jwtService;

    public String mintServiceToken() {
        return "Bearer " + jwtService.generateServiceToken(SERVICE_NAME);
    }
}
