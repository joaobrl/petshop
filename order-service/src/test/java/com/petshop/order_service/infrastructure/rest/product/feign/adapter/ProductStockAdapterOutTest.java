package com.petshop.order_service.infrastructure.rest.product.feign.adapter;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.order_service.infrastructure.rest.product.feign.ProductFeign;
import com.petshop.order_service.infrastructure.rest.product.feign.dto.ProductStockResponseDto;
import com.petshop.order_service.infrastructure.rest.product.feign.dto.StockAdjustmentRequestDto;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductStockAdapterOutTest {

    @Mock
    private ProductFeign productFeign;

    private ProductStockAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProductStockAdapterOut(productFeign);
    }

    private FeignException feignException(int status, String body) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/products/1",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(status)
                .reason("reason")
                .request(request)
                .headers(Collections.emptyMap())
                .body(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8))
                .build();
        return FeignException.errorStatus("ProductFeign#call", response);
    }

    private ProductStockResponseDto sampleResponse() {
        var dto = new ProductStockResponseDto();
        dto.setId(1L);
        dto.setName("Racao");
        dto.setPrice(79.90);
        dto.setStock(50);
        dto.setReservedStock(0);
        dto.setAvailableStock(50);
        return dto;
    }

    @Nested
    class GetInfo {

        @Test
        void returnsMappedInfoOnSuccess() {
            when(productFeign.findById(1L)).thenReturn(sampleResponse());

            var info = adapter.getInfo(1L);

            assertThat(info.id()).isEqualTo(1L);
            assertThat(info.name()).isEqualTo("Racao");
            assertThat(info.price()).isEqualTo(79.90);
        }

        @Test
        void throwsNotFoundWhenProductMissing() {
            when(productFeign.findById(1L)).thenThrow(feignException(404, null));

            assertThatThrownBy(() -> adapter.getInfo(1L)).isInstanceOf(NotFoundException.class);
        }

        @Test
        void throwsBusinessRuleWithDetailExtractedFromProblemDetailBody() {
            when(productFeign.findById(1L)).thenThrow(feignException(503, "{\"detail\":\"Serviço indisponível\"}"));

            assertThatThrownBy(() -> adapter.getInfo(1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Serviço indisponível");
        }

        @Test
        void throwsBusinessRuleWithFallbackMessageWhenBodyIsBlank() {
            when(productFeign.findById(1L)).thenThrow(feignException(503, ""));

            assertThatThrownBy(() -> adapter.getInfo(1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Não foi possível consultar o produto 1");
        }

        @Test
        void throwsBusinessRuleWithFallbackMessageWhenBodyIsNotValidJson() {
            when(productFeign.findById(1L)).thenThrow(feignException(503, "not-json"));

            assertThatThrownBy(() -> adapter.getInfo(1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Não foi possível consultar o produto 1");
        }

        @Test
        void throwsBusinessRuleWithFallbackMessageWhenDetailFieldIsMissing() {
            when(productFeign.findById(1L)).thenThrow(feignException(503, "{\"title\":\"Erro\"}"));

            assertThatThrownBy(() -> adapter.getInfo(1L))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Não foi possível consultar o produto 1");
        }
    }

    @Nested
    class Reserve {

        @Test
        void returnsMappedInfoOnSuccess() {
            when(productFeign.reserve(1L, new StockAdjustmentRequestDto(5))).thenReturn(sampleResponse());

            var info = adapter.reserve(1L, 5);

            assertThat(info.id()).isEqualTo(1L);
        }

        @Test
        void throwsNotFoundWhenProductMissing() {
            when(productFeign.reserve(1L, new StockAdjustmentRequestDto(5))).thenThrow(feignException(404, null));

            assertThatThrownBy(() -> adapter.reserve(1L, 5)).isInstanceOf(NotFoundException.class);
        }

        @Test
        void throwsBusinessRuleOnOtherFeignFailures() {
            when(productFeign.reserve(1L, new StockAdjustmentRequestDto(5)))
                    .thenThrow(feignException(422, "{\"detail\":\"Estoque insuficiente\"}"));

            assertThatThrownBy(() -> adapter.reserve(1L, 5))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Estoque insuficiente");
        }
    }

    @Nested
    class Release {

        @Test
        void callsFeignWithoutThrowingOnSuccess() {
            adapter.release(1L, 3);

            verify(productFeign).release(1L, new StockAdjustmentRequestDto(3));
        }

        @Test
        void swallowsNotFoundAndLogsInstead() {
            when(productFeign.release(1L, new StockAdjustmentRequestDto(3))).thenThrow(feignException(404, null));

            assertThatCode(() -> adapter.release(1L, 3)).doesNotThrowAnyException();

            verify(productFeign).release(1L, new StockAdjustmentRequestDto(3));
        }

        @Test
        void swallowsAnyOtherFeignFailureAndLogsInstead() {
            when(productFeign.release(1L, new StockAdjustmentRequestDto(3))).thenThrow(feignException(503, null));

            assertThatCode(() -> adapter.release(1L, 3)).doesNotThrowAnyException();

            verify(productFeign).release(1L, new StockAdjustmentRequestDto(3));
        }
    }

    @Nested
    class Confirm {

        @Test
        void callsFeignWithoutThrowingOnSuccess() {
            adapter.confirm(1L, 2);

            verify(productFeign).confirm(1L, new StockAdjustmentRequestDto(2));
        }

        @Test
        void swallowsFeignFailureAndLogsInstead() {
            when(productFeign.confirm(1L, new StockAdjustmentRequestDto(2))).thenThrow(feignException(503, null));

            assertThatCode(() -> adapter.confirm(1L, 2)).doesNotThrowAnyException();

            verify(productFeign).confirm(1L, new StockAdjustmentRequestDto(2));
        }
    }
}
