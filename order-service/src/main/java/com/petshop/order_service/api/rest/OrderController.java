package com.petshop.order_service.api.rest;

import com.petshop.order_service.api.rest.dto.OrderResponseDto;
import org.springframework.http.ResponseEntity;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RequestMapping("/api/v1/orders")
public interface OrderController {

    @PostMapping("/checkout")
    ResponseEntity<OrderResponseDto> checkout(@RequestParam UUID customerId, UriComponentsBuilder uriBuilder, @AuthenticationPrincipal AuthenticatedUser user);

    @GetMapping("/{id}")
    ResponseEntity<OrderResponseDto> getOrderById(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user);
}
