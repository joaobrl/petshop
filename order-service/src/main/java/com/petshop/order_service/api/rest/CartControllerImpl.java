package com.petshop.order_service.api.rest;

import com.petshop.commons.security.jwt.JwtService;
import com.petshop.order_service.api.rest.dto.CartResponseDto;
import com.petshop.order_service.core.port.in.CartPortIn;
import com.petshop.order_service.core.port.in.dto.AddCartItemRequestDto;
import com.petshop.order_service.infrastructure.security.CustomerIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CartControllerImpl implements CartController {

    private final CartPortIn cartPortIn;
    private final CustomerIdentityResolver customerIdentityResolver;
    private final JwtService jwtService;

    @Override
    public ResponseEntity<CartResponseDto> addItem(AddCartItemRequestDto request, String token) {
        var user = jwtService.parseAuthorizationHeaderOrNull(token);
        var customerId = customerIdentityResolver.resolveCustomerId(request.getCustomerId(), user);
        var cart = cartPortIn.addItem(customerId, request.getProductId(), request.getQuantity(), token);
        return ResponseEntity.ok(new CartResponseDto(cart));
    }

    @Override
    public ResponseEntity<CartResponseDto> removeItem(Long productId, UUID customerId, String token) {
        var user = jwtService.parseAuthorizationHeaderOrNull(token);
        var resolvedCustomerId = customerIdentityResolver.resolveCustomerId(customerId, user);
        var cart = cartPortIn.removeItem(resolvedCustomerId, productId, token);
        return ResponseEntity.ok(new CartResponseDto(cart));
    }

    @Override
    public ResponseEntity<CartResponseDto> getCart(UUID customerId, String token) {
        var user = jwtService.parseAuthorizationHeaderOrNull(token);
        var resolvedCustomerId = customerIdentityResolver.resolveCustomerId(customerId, user);
        var cart = cartPortIn.getCart(resolvedCustomerId, token);
        return ResponseEntity.ok(new CartResponseDto(cart));
    }
}
