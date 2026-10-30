package com.petshop.order_service.api.rest;

import com.petshop.commons.security.jwt.JwtService;
import com.petshop.order_service.api.rest.dto.OrderResponseDto;
import com.petshop.order_service.core.port.in.OrderPortIn;
import com.petshop.order_service.core.port.in.dto.CheckoutRequestDto;
import com.petshop.order_service.infrastructure.security.CustomerIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderControllerImpl implements OrderController {

    private final OrderPortIn orderPortIn;
    private final CustomerIdentityResolver customerIdentityResolver;
    private final JwtService jwtService;

    @Override
    public ResponseEntity<OrderResponseDto> checkout(UUID customerId, CheckoutRequestDto request, String idempotencyKey, UriComponentsBuilder uriBuilder, String token) {
        var user = jwtService.parseAuthorizationHeaderOrNull(token);
        var resolvedCustomerId = customerIdentityResolver.resolveCustomerId(customerId, user);
        var result = orderPortIn.checkout(resolvedCustomerId, request.getProductIds(), idempotencyKey, token);
        var body = new OrderResponseDto(result.order());
        if (!result.created()) {
            return ResponseEntity.ok(body);
        }
        var uri = uriBuilder.path("/api/v1/orders/{id}").buildAndExpand(result.order().getId()).toUri();
        return ResponseEntity.created(uri).body(body);
    }

    @Override
    public ResponseEntity<OrderResponseDto> getOrderById(UUID id, String token) {
        var user = jwtService.parseAuthorizationHeaderOrNull(token);
        var order = orderPortIn.findById(id);
        customerIdentityResolver.requireOwnershipIfCustomer(order.getCustomerId(), user);
        return ResponseEntity.ok(new OrderResponseDto(order));
    }
}
