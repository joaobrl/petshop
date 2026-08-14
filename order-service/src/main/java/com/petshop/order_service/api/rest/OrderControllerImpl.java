package com.petshop.order_service.api.rest;

import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.order_service.api.rest.dto.OrderResponseDto;
import com.petshop.order_service.core.port.in.OrderPortIn;
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

    @Override
    public ResponseEntity<OrderResponseDto> checkout(UUID customerId, UriComponentsBuilder uriBuilder, AuthenticatedUser user) {
        var resolvedCustomerId = customerIdentityResolver.resolveCustomerId(customerId, user);
        var order = orderPortIn.checkout(resolvedCustomerId);
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
