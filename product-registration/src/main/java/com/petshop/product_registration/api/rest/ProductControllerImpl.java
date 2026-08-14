package com.petshop.product_registration.api.rest;

import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.product_registration.api.rest.dto.ProductResponseDto;
import com.petshop.product_registration.core.port.in.ProductPortIn;
import com.petshop.product_registration.core.port.in.dto.ProductRequestDto;
import com.petshop.product_registration.core.port.in.dto.StockAdjustmentRequestDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ProductControllerImpl implements ProductController {

    private final ProductPortIn portIn;

    @Override
    public ResponseEntity<ProductResponseDto> registerProduct(@Valid @RequestBody ProductRequestDto productRequest, UriComponentsBuilder uriBuilder) {
        var product = portIn.registerProduct(productRequest);
        var uri = uriBuilder.path("/products/{id}").buildAndExpand(product.getId()).toUri();
        return ResponseEntity.created(uri).body(new ProductResponseDto(product));
    }

    @Override
    public ResponseEntity<List<ProductResponseDto>> listProducts(AuthenticatedUser user) {
        boolean includeStock = user != null && user.type() == AccountType.STAFF;
        var products = portIn.listProducts()
                .stream()
                .map(product -> new ProductResponseDto(product, includeStock))
                .toList();
        return ResponseEntity.ok(products);
    }

    @Override
    public ResponseEntity<ProductResponseDto> getProductById(Long id, AuthenticatedUser user) {
        boolean includeStock = user != null && user.type() == AccountType.STAFF;
        var product = portIn.getProductById(id);
        return ResponseEntity.ok(new ProductResponseDto(product, includeStock));
    }

    @Override
    public ResponseEntity<ProductResponseDto> updateProduct(Long id, ProductRequestDto productRequest) {
        var updatedProduct = portIn.updateProduct(id, productRequest);
        return ResponseEntity.ok(new ProductResponseDto(updatedProduct));
    }

    @Override
    public ResponseEntity<Void> deleteProduct(Long id) {
        portIn.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ProductResponseDto> reserveStock(Long id, @Valid StockAdjustmentRequestDto request) {
        var product = portIn.reserveStock(id, request.getQuantity());
        return ResponseEntity.ok(new ProductResponseDto(product));
    }

    @Override
    public ResponseEntity<ProductResponseDto> releaseStock(Long id, @Valid StockAdjustmentRequestDto request) {
        var product = portIn.releaseStock(id, request.getQuantity());
        return ResponseEntity.ok(new ProductResponseDto(product));
    }

    @Override
    public ResponseEntity<ProductResponseDto> confirmStock(Long id, @Valid StockAdjustmentRequestDto request) {
        var product = portIn.confirmStock(id, request.getQuantity());
        return ResponseEntity.ok(new ProductResponseDto(product));
    }
}
