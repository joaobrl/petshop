package com.petshop.order_service.infrastructure.rest.product.feign.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.order_service.core.domain.ProductStockInfo;
import com.petshop.order_service.core.port.out.ProductStockPortOut;
import com.petshop.order_service.infrastructure.rest.product.feign.ProductFeign;
import com.petshop.order_service.infrastructure.rest.product.feign.dto.StockAdjustmentRequestDto;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductStockAdapterOut implements ProductStockPortOut {

    private final ProductFeign productFeign;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ProductStockInfo getInfo(Long productId) {
        try {
            var response = productFeign.findById(productId);
            return new ProductStockInfo(response.getId(), response.getName(), response.getPrice());
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Produto", productId);
        } catch (FeignException e) {
            throw new BusinessRuleException(extractDetail(e, "Não foi possível consultar o produto " + productId));
        }
    }

    @Override
    public ProductStockInfo reserve(Long productId, int quantity) {
        try {
            var response = productFeign.reserve(productId, new StockAdjustmentRequestDto(quantity));
            return new ProductStockInfo(response.getId(), response.getName(), response.getPrice());
        } catch (FeignException.NotFound e) {
            throw new NotFoundException("Produto", productId);
        } catch (FeignException e) {
            throw new BusinessRuleException(extractDetail(e, "Não foi possível reservar o produto " + productId));
        }
    }

    @Override
    public void release(Long productId, int quantity) {
        try {
            productFeign.release(productId, new StockAdjustmentRequestDto(quantity));
        } catch (FeignException.NotFound e) {
            log.warn("Produto {} não encontrado ao tentar liberar reserva de estoque.", productId);
        } catch (FeignException e) {
            log.error("Falha ao liberar reserva de estoque do produto {}: {}", productId, e.getMessage());
        }
    }

    @Override
    public void confirm(Long productId, int quantity) {
        try {
            productFeign.confirm(productId, new StockAdjustmentRequestDto(quantity));
        } catch (FeignException e) {
            log.error("Falha ao confirmar baixa definitiva de estoque do produto {}: {}", productId, e.getMessage());
        }
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
