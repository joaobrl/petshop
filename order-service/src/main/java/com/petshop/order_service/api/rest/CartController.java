package com.petshop.order_service.api.rest;

import com.petshop.order_service.api.rest.dto.CartResponseDto;
import com.petshop.order_service.core.port.in.dto.AddCartItemRequestDto;
import jakarta.validation.Valid;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequestMapping("/api/v1/cart")
public interface CartController {

    @PostMapping("/items")
    ResponseEntity<CartResponseDto> addItem(@Valid @RequestBody AddCartItemRequestDto request, @AuthenticationPrincipal AuthenticatedUser user, @RequestHeader(value = "Authorization", required = false) String token);

    @DeleteMapping("/items/{productId}")
    ResponseEntity<CartResponseDto> removeItem(@PathVariable Long productId, @RequestParam UUID customerId, @AuthenticationPrincipal AuthenticatedUser user, @RequestHeader(value = "Authorization", required = false) String token);

    @GetMapping
    ResponseEntity<CartResponseDto> getCart(@RequestParam UUID customerId, @AuthenticationPrincipal AuthenticatedUser user, @RequestHeader(value = "Authorization", required = false) String token);
}
