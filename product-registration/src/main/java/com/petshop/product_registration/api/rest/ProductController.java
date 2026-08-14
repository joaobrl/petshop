package com.petshop.product_registration.api.rest;

import com.petshop.commons.dto.PageResponse;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.product_registration.api.rest.dto.ProductResponseDto;
import com.petshop.product_registration.core.port.in.dto.ProductRequestDto;
import com.petshop.product_registration.core.port.in.dto.StockAdjustmentRequestDto;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@code GET /list} e {@code GET /find/{id}} aceitam token opcional: o
 * {@code JwtAuthenticationFilter} popula {@code AuthenticatedUser} mesmo em
 * endpoint {@code permitAll()} — sem token, {@code user} chega null e o
 * endpoint continua público. Estoque só entra na resposta quando
 * {@code user.type() == AccountType.STAFF} (ver ProductResponseDto).
 * Matriz completa de acesso: ver SecurityConfig.
 */
@RequestMapping("/api/v1/products")
public interface ProductController {

    @PostMapping("/register")
    ResponseEntity<ProductResponseDto> registerProduct(@Valid @RequestBody ProductRequestDto productRequest, UriComponentsBuilder uriBuilder);

    @GetMapping("/list")
    ResponseEntity<PageResponse<ProductResponseDto>> listProducts(@PageableDefault(size = 20) Pageable pageable, @AuthenticationPrincipal AuthenticatedUser user);

    @GetMapping("/find/{id}")
    ResponseEntity<ProductResponseDto> getProductById(@PathVariable Long id, @AuthenticationPrincipal AuthenticatedUser user);

    @PatchMapping("/update/{id}")
    ResponseEntity<ProductResponseDto> updateProduct(@PathVariable Long id, @RequestBody ProductRequestDto productRequest);

    @DeleteMapping("/delete/{id}")
    ResponseEntity<Void> deleteProduct(@PathVariable Long id);

    @PostMapping("/{id}/reserve")
    ResponseEntity<ProductResponseDto> reserveStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequestDto request);

    @PostMapping("/{id}/release")
    ResponseEntity<ProductResponseDto> releaseStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequestDto request);

    @PostMapping("/{id}/confirm")
    ResponseEntity<ProductResponseDto> confirmStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequestDto request);
}
