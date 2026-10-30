package com.petshop.order_service.core.domain.enums;

/**
 * Disponibilidade de um item do carrinho, calculada a cada leitura a partir
 * da faixa de estoque do product-registration (nunca a quantidade exata).
 */
public enum StockAvailability {
    /** Faixa "20+" ou "50+". */
    AVAILABLE,
    /** 1 a 19 disponíveis — front mostra "Poucas unidades". */
    LOW,
    /** Sem estoque — item fica no carrinho, mas fora do total e da seleção. */
    UNAVAILABLE
}
