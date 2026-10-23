package com.petshop.order_service.core.port.out;

import com.petshop.order_service.core.domain.ProductStockInfo;

public interface ProductStockPortOut {

    /**
     * Consulta o produto sem reservar nada — usada quando o item é
     * adicionado ao carrinho por quem não está logado (ver CartService).
     */
    ProductStockInfo getInfo(Long productId, String token);

    /**
     * Reserva a quantidade no product-registration. Lança BusinessRuleException
     * (via petshop-commons) se não houver estoque disponível. {@code token} é o
     * Authorization de quem chamou, repassado via TokenPropagation; sem requisição
     * HTTP em curso (ex.: scheduler), passar {@code null} assina um token de serviço.
     */
    ProductStockInfo reserve(Long productId, int quantity, String token);

    /**
     * Libera uma reserva (item removido do carrinho ou carrinho expirado).
     */
    void release(Long productId, int quantity, String token);

    /**
     * Baixa definitiva de estoque (pagamento confirmado).
     */
    void confirm(Long productId, int quantity, String token);
}
