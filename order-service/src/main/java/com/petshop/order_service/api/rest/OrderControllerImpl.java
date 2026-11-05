package com.petshop.order_service.api.rest;

import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.order_service.api.rest.dto.OrderResponseDto;
import com.petshop.order_service.core.port.in.OrderPortIn;
import com.petshop.order_service.core.port.in.dto.CheckoutRequestDto;
import com.petshop.order_service.infrastructure.security.CustomerIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OrderControllerImpl implements OrderController {

    static final String EMAIL_NOT_VERIFIED_MESSAGE = "Confirme seu e-mail para continuar";

    private final OrderPortIn orderPortIn;
    private final CustomerIdentityResolver customerIdentityResolver;

    @Override
    public ResponseEntity<OrderResponseDto> checkout(UUID customerId, CheckoutRequestDto request, UriComponentsBuilder uriBuilder, AuthenticatedUser user, String token) {
        // SDD-020: cliente com e-mail não confirmado monta carrinho, mas não fecha pedido.
        if (user != null && user.isUnverifiedCustomer()) {
            throw new AccessDeniedException(EMAIL_NOT_VERIFIED_MESSAGE);
        }
        var resolvedCustomerId = customerIdentityResolver.resolveCustomerId(customerId, user);
        var order = orderPortIn.checkout(resolvedCustomerId, request.getProductIds(), token);
        var uri = uriBuilder.path("/api/v1/orders/{id}").buildAndExpand(order.getId()).toUri();
        return ResponseEntity.created(uri).body(new OrderResponseDto(order));
    }

    @Override
    public ResponseEntity<OrderResponseDto> getOrderById(UUID id, AuthenticatedUser user) {
        var order = orderPortIn.findById(id);
        customerIdentityResolver.requireOwnershipIfCustomer(order.getCustomerId(), user);
        return ResponseEntity.ok(new OrderResponseDto(order));
    }
}
