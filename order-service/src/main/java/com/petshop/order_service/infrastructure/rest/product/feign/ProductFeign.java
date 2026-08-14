package com.petshop.order_service.infrastructure.rest.product.feign;

import com.petshop.order_service.infrastructure.rest.product.feign.dto.ProductStockResponseDto;
import com.petshop.order_service.infrastructure.rest.product.feign.dto.StockAdjustmentRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-registration", url = "${external.api.product-service.url}")
public interface ProductFeign {

    /**
     * Consulta só leitura (endpoint público do catálogo) — usada quando o
     * item é adicionado ao carrinho sem login, ou seja, sem reservar
     * estoque nenhum.
     */
    @GetMapping("/api/v1/products/find/{id}")
    ProductStockResponseDto findById(@PathVariable("id") Long id);

    @PostMapping("/api/v1/products/{id}/reserve")
    ProductStockResponseDto reserve(@PathVariable("id") Long id, @RequestBody StockAdjustmentRequestDto request);

    @PostMapping("/api/v1/products/{id}/release")
    ProductStockResponseDto release(@PathVariable("id") Long id, @RequestBody StockAdjustmentRequestDto request);

    @PostMapping("/api/v1/products/{id}/confirm")
    ProductStockResponseDto confirm(@PathVariable("id") Long id, @RequestBody StockAdjustmentRequestDto request);
}
