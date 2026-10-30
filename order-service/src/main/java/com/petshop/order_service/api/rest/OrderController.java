package com.petshop.order_service.api.rest;

import com.petshop.order_service.api.rest.dto.OrderResponseDto;
import com.petshop.order_service.core.port.in.dto.CheckoutRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.UUID;

@RequestMapping("/api/v1/orders")
public interface OrderController {

    @PostMapping("/checkout")
    ResponseEntity<OrderResponseDto> checkout(@RequestParam UUID customerId, @RequestBody CheckoutRequestDto request, UriComponentsBuilder uriBuilder, @RequestHeader("Authorization") String token);

    @GetMapping("/{id}")
    ResponseEntity<OrderResponseDto> getOrderById(@PathVariable UUID id, @RequestHeader("Authorization") String token);
}
