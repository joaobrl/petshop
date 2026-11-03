package com.petshop.order_service.api.rest;

import com.petshop.order_service.api.rest.dto.OrderResponseDto;
import com.petshop.order_service.core.port.in.dto.CheckoutRequestDto;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RequestMapping("/api/v1/orders")
public interface OrderController {

    @PostMapping("/checkout")
    ResponseEntity<OrderResponseDto> checkout(@RequestParam UUID customerId, @RequestBody CheckoutRequestDto request, UriComponentsBuilder uriBuilder, @AuthenticationPrincipal AuthenticatedUser user, @RequestHeader("Authorization") String token);

    @GetMapping("/{id}")
    ResponseEntity<OrderResponseDto> getOrderById(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser user);
}
