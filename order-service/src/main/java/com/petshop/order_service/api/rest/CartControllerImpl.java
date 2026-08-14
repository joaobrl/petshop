package com.petshop.order_service.api.rest;

import com.petshop.commons.security.jwt.AuthenticatedUser;
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

    @Override
    public ResponseEntity<CartResponseDto> addItem(AddCartItemRequestDto request, AuthenticatedUser user) {
        var customerId = customerIdentityResolver.resolveCustomerId(request.getCustomerId(), user);
        boolean reserveStock = user != null;
        var cart = cartPortIn.addItem(customerId, request.getProductId(), request.getQuantity(), reserveStock);
        return ResponseEntity.ok(new CartResponseDto(cart));
    }

    @Override
    public ResponseEntity<CartResponseDto> removeItem(Long productId, UUID customerId, AuthenticatedUser user) {
        var resolvedCustomerId = customerIdentityResolver.resolveCustomerId(customerId, user);
        var cart = cartPortIn.removeItem(resolvedCustomerId, productId);
        return ResponseEntity.ok(new CartResponseDto(cart));
    }

    @Override
    public ResponseEntity<CartResponseDto> getCart(UUID customerId, AuthenticatedUser user) {
        var resolvedCustomerId = customerIdentityResolver.resolveCustomerId(customerId, user);
        var cart = cartPortIn.getCart(resolvedCustomerId);
        return ResponseEntity.ok(new CartResponseDto(cart));
    }
}
