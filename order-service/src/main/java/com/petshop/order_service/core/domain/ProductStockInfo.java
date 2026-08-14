package com.petshop.order_service.core.domain;

/**
 * Retrato do produto obtido via Feign do product-registration, no momento
 * da reserva de estoque — usado pra tirar o "retrato" salvo no CartItem.
 */
public record ProductStockInfo(Long id, String name, Double price) {
}
