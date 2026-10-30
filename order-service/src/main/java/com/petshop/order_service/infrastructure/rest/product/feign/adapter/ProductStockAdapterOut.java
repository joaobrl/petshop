package com.petshop.order_service.infrastructure.rest.product.feign.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.order_service.core.domain.ProductStockInfo;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import com.petshop.order_service.infrastructure.rest.product.feign.ProductFeign;
import com.petshop.order_service.infrastructure.rest.product.feign.dto.ProductStockResponseDto;
import com.petshop.order_service.infrastructure.rest.product.feign.dto.StockAdjustmentRequestDto;
import com.petshop.order_service.infrastructure.security.TokenPropagation;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductStockAdapterOut implements ProductStockPortOut {

    private final ProductFeign productFeign;
    private final TokenPropagation tokenPropagation;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ProductStockInfo getInfo(Long productId, String token) {
        try {
            return toInfo(productFeign.findById(productId, tokenPropagation.resolveAuthorizationHeader(token)));
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Produto", productId);
        } catch (FeignException e) {
            throw new BusinessRuleException(extractDetail(e, "Não foi possível consultar o produto " + productId));
        }
    }

    @Override
    public Map<Long, ProductStockInfo> getInfos(Collection<Long> productIds, String token) {
        try {
            var response = productFeign.findByIds(List.copyOf(productIds), tokenPropagation.resolveAuthorizationHeader(token));
            return response.stream().map(this::toInfo)
                    .collect(Collectors.toMap(ProductStockInfo::id, Function.identity(), (first, second) -> first));
        } catch (FeignException e) {
            throw new BusinessRuleException(extractDetail(e, "Não foi possível consultar os produtos " + productIds));
        }
    }

    // reserve/release/confirm são rotas internas do product-registration (chamadas só pelo
    // order-service) — repassam o token de quem chamou quando há um (relay-com-fallback);
    // sem requisição HTTP em curso (ex.: PaymentMockScheduler), assina um token de serviço.
    @Override
    public ProductStockInfo reserve(Long productId, int quantity, String token) {
        try {
            return toInfo(productFeign.reserve(productId, new StockAdjustmentRequestDto(quantity), tokenPropagation.resolveAuthorizationHeader(token)));
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Produto", productId);
        } catch (FeignException e) {
            throw new BusinessRuleException(extractDetail(e, "Não foi possível reservar o produto " + productId));
        }
    }

    @Override
    public void release(Long productId, int quantity, String token) {
        try {
            productFeign.release(productId, new StockAdjustmentRequestDto(quantity), tokenPropagation.resolveAuthorizationHeader(token));
        } catch (FeignException.NotFound e) {
            log.warn("Produto {} não encontrado ao tentar liberar reserva de estoque.", productId);
        } catch (FeignException e) {
            log.error("Falha ao liberar reserva de estoque do produto {}: {}", productId, e.getMessage());
        }
    }

    @Override
    public void confirm(Long productId, int quantity, String token) {
        try {
            productFeign.confirm(productId, new StockAdjustmentRequestDto(quantity), tokenPropagation.resolveAuthorizationHeader(token));
        } catch (FeignException e) {
            log.error("Falha ao confirmar baixa definitiva de estoque do produto {}: {}", productId, e.getMessage());
        }
    }

    private ProductStockInfo toInfo(ProductStockResponseDto response) {
        return new ProductStockInfo(response.getId(), response.getName(), response.getPrice(),
                response.getEnabled(), response.getStockRange());
    }

    /**
     * O product-registration devolve erros no formato ProblemDetail (RFC 7807),
     * com a mensagem de negócio no campo "detail". Tenta extrair isso antes de
     * cair pra uma mensagem genérica.
     */
    private String extractDetail(FeignException e, String fallback) {
        try {
            var body = e.contentUTF8();
            if (body == null || body.isBlank()) {
                return fallback;
            }
            var node = objectMapper.readTree(body);
            var detail = node.get("detail");
            return detail != null ? detail.asText() : fallback;
        } catch (Exception parseError) {
            return fallback;
        }
    }
}
