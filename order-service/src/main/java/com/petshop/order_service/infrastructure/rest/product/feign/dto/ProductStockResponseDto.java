package com.petshop.order_service.infrastructure.rest.product.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Espelha só os campos que o order-service precisa de ProductResponseDto
 * (product-registration). @JsonIgnoreProperties pra não quebrar se o
 * product-registration devolver campos extras (ex.: dimensions).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductStockResponseDto {
    private Long id;
    private String name;
    private Double price;
    private Integer stock;
    private Integer reservedStock;
    private Integer availableStock;
}
