package com.petshop.order_service.infrastructure.rest.product.feign;

import com.petshop.order_service.infrastructure.rest.product.feign.dto.ProductStockResponseDto;
import com.petshop.order_service.infrastructure.rest.product.feign.dto.StockAdjustmentRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "product-registration", url = "${external.api.product-service.url}")
public interface ProductFeign {

    /**
     * Consulta só leitura (endpoint público do catálogo) — usada quando o
     * item é adicionado ao carrinho, que não reserva estoque.
     */
    @GetMapping("/api/v1/products/find/{id}")
    ProductStockResponseDto findById(@PathVariable("id") Long id, @RequestHeader("Authorization") String authorization);

    /**
     * Busca em lote (endpoint público do catálogo): uma chamada só para
     * todos os produtos do carrinho, inclusive os desativados.
     */
    @GetMapping("/api/v1/products/find")
    List<ProductStockResponseDto> findByIds(@RequestParam("ids") List<Long> ids, @RequestHeader("Authorization") String authorization);

    @PostMapping("/api/v1/products/{id}/reserve")
    ProductStockResponseDto reserve(@PathVariable("id") Long id, @RequestBody StockAdjustmentRequestDto request, @RequestHeader("Authorization") String authorization);

    @PostMapping("/api/v1/products/{id}/release")
    ProductStockResponseDto release(@PathVariable("id") Long id, @RequestBody StockAdjustmentRequestDto request, @RequestHeader("Authorization") String authorization);

    @PostMapping("/api/v1/products/{id}/confirm")
    ProductStockResponseDto confirm(@PathVariable("id") Long id, @RequestBody StockAdjustmentRequestDto request, @RequestHeader("Authorization") String authorization);
}
