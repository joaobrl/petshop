package com.petshop.order_service.core.port.out;

import com.petshop.order_service.core.domain.ProductStockInfo;

import java.util.Collection;
import java.util.Map;

public interface ProductStockPortOut {

    /**
     * Consulta o produto sem reservar nada — usada ao adicionar item ao
     * carrinho (o carrinho não reserva estoque; ver CartService).
     */
    ProductStockInfo getInfo(Long productId, String token);

    /**
     * Consulta vários produtos numa chamada só (inclusive desativados). Id
     * que não vem no mapa é produto que não existe mais. Lança
     * BusinessRuleException se o product-registration não responder.
     */
    Map<Long, ProductStockInfo> getInfos(Collection<Long> productIds, String token);

    /**
     * Reserva a quantidade no product-registration (só no checkout). Lança
     * BusinessRuleException (via petshop-commons) se não houver estoque ou o
     * produto estiver desativado. {@code token} é o Authorization de quem
     * chamou, repassado via TokenPropagation; sem requisição HTTP em curso
     * (ex.: scheduler), passar {@code null} assina um token de serviço.
     */
    ProductStockInfo reserve(Long productId, int quantity, String token);

    /**
     * Libera uma reserva (checkout que falhou no meio ou pedido expirado).
     */
    void release(Long productId, int quantity, String token);

    /**
     * Baixa definitiva de estoque (pagamento confirmado).
     */
    void confirm(Long productId, int quantity, String token);
}
