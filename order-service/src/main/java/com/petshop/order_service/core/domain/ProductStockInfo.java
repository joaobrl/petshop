package com.petshop.order_service.core.domain;

import com.petshop.order_service.core.domain.enums.StockAvailability;

/**
 * Retrato do produto obtido via Feign do product-registration — usado pra
 * tirar o "retrato" salvo no CartItem e pra marcar a disponibilidade do
 * item no carrinho. {@code stockRange} é a faixa ("50+", "20+", "LOW" ou
 * null sem estoque); a quantidade exata nunca chega aqui.
 */
public record ProductStockInfo(Long id, String name, Double price, Boolean enabled, String stockRange) {

    private static final String STOCK_RANGE_LOW = "LOW";

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public StockAvailability availability() {
        if (stockRange == null) return StockAvailability.UNAVAILABLE;
        if (STOCK_RANGE_LOW.equals(stockRange)) return StockAvailability.LOW;
        return StockAvailability.AVAILABLE;
    }
}
