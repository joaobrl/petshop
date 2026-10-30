package com.petshop.order_service.infrastructure.rest.product.feign.adapter;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.NotFoundException;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.order_service.infrastructure.rest.product.feign.ProductFeign;
import com.petshop.order_service.infrastructure.rest.product.feign.dto.ProductStockResponseDto;
import com.petshop.order_service.infrastructure.rest.product.feign.dto.StockAdjustmentRequestDto;
import com.petshop.order_service.infrastructure.security.TokenPropagation;
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
import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductStockAdapterOutTest {

    private static final JwtService JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Mock
    private ProductFeign productFeign;

    private ProductStockAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new ProductStockAdapterOut(productFeign, new TokenPropagation(JWT_SERVICE));
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
    class GetInfos {

        @Test
        void mapsBatchResponseByIdInASingleCall() {
            var disabled = sampleResponse();
            disabled.setId(2L);
            disabled.setEnabled(false);
            var enabled = sampleResponse();
            enabled.setEnabled(true);
            enabled.setStockRange("LOW");
            when(productFeign.findByIds(eq(List.of(1L, 2L, 3L)), anyString())).thenReturn(List.of(enabled, disabled));

            var infos = adapter.getInfos(List.of(1L, 2L, 3L), "Bearer user-token");

            assertThat(infos).containsOnlyKeys(1L, 2L);
            assertThat(infos.get(1L).stockRange()).isEqualTo("LOW");
            assertThat(infos.get(2L).isEnabled()).isFalse();
            verify(productFeign, times(1)).findByIds(List.of(1L, 2L, 3L), "Bearer user-token");
        }

        @Test
        void throwsBusinessRuleWhenProductServiceFails() {
            when(productFeign.findByIds(any(), anyString())).thenThrow(feignException(503, null));

            assertThatThrownBy(() -> adapter.getInfos(List.of(1L), "Bearer user-token"))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    class GetInfo {

        @Test
        void returnsMappedInfoOnSuccess() {
            var response = sampleResponse();
            response.setEnabled(true);
            response.setStockRange("50+");
            when(productFeign.findById(eq(1L), anyString())).thenReturn(response);

            var info = adapter.getInfo(1L, "Bearer user-token");

            assertThat(info.id()).isEqualTo(1L);
            assertThat(info.name()).isEqualTo("Racao");
            assertThat(info.price()).isEqualTo(79.90);
            assertThat(info.isEnabled()).isTrue();
            assertThat(info.stockRange()).isEqualTo("50+");
        }

        @Test
        void relaysTheGivenTokenToFeign() {
            when(productFeign.findById(eq(1L), anyString())).thenReturn(sampleResponse());

            adapter.getInfo(1L, "Bearer user-token");

            verify(productFeign).findById(1L, "Bearer user-token");
        }

        @Test
        void mintsAServiceTokenWhenNoTokenIsGiven() {
            when(productFeign.findById(eq(1L), anyString())).thenReturn(sampleResponse());

            var info = adapter.getInfo(1L, null);

            assertThat(info.id()).isEqualTo(1L);
        }

        @Test
        void throwsNotFoundWhenProductMissing() {
            when(productFeign.findById(eq(1L), anyString())).thenThrow(feignException(404, null));

            assertThatThrownBy(() -> adapter.getInfo(1L, "Bearer user-token")).isInstanceOf(NotFoundException.class);
        }

        @Test
        void throwsBusinessRuleWithDetailExtractedFromProblemDetailBody() {
            when(productFeign.findById(eq(1L), anyString())).thenThrow(feignException(503, "{\"detail\":\"Serviço indisponível\"}"));

            assertThatThrownBy(() -> adapter.getInfo(1L, "Bearer user-token"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Serviço indisponível");
        }

        @Test
        void throwsBusinessRuleWithFallbackMessageWhenBodyIsBlank() {
            when(productFeign.findById(eq(1L), anyString())).thenThrow(feignException(503, ""));

            assertThatThrownBy(() -> adapter.getInfo(1L, "Bearer user-token"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Não foi possível consultar o produto 1");
        }

        @Test
        void throwsBusinessRuleWithFallbackMessageWhenBodyIsNotValidJson() {
            when(productFeign.findById(eq(1L), anyString())).thenThrow(feignException(503, "not-json"));

            assertThatThrownBy(() -> adapter.getInfo(1L, "Bearer user-token"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Não foi possível consultar o produto 1");
        }

        @Test
        void throwsBusinessRuleWithFallbackMessageWhenDetailFieldIsMissing() {
            when(productFeign.findById(eq(1L), anyString())).thenThrow(feignException(503, "{\"title\":\"Erro\"}"));

            assertThatThrownBy(() -> adapter.getInfo(1L, "Bearer user-token"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Não foi possível consultar o produto 1");
        }
    }

    @Nested
    class Reserve {

        @Test
        void returnsMappedInfoOnSuccess() {
            when(productFeign.reserve(eq(1L), eq(new StockAdjustmentRequestDto(5)), anyString())).thenReturn(sampleResponse());

            var info = adapter.reserve(1L, 5, null);

            assertThat(info.id()).isEqualTo(1L);
        }

        @Test
        void mintsAServiceTokenWhenNoCallerTokenIsGiven() {
            when(productFeign.reserve(eq(1L), eq(new StockAdjustmentRequestDto(5)), anyString())).thenReturn(sampleResponse());

            adapter.reserve(1L, 5, null);

            var captor = org.mockito.ArgumentCaptor.forClass(String.class);
            verify(productFeign).reserve(eq(1L), eq(new StockAdjustmentRequestDto(5)), captor.capture());
            var parsed = JWT_SERVICE.parse(captor.getValue().substring("Bearer ".length()));
            assertThat(parsed.type().name()).isEqualTo("SERVICE");
        }

        @Test
        void relaysTheCallerTokenWhenGiven() {
            when(productFeign.reserve(eq(1L), eq(new StockAdjustmentRequestDto(5)), anyString())).thenReturn(sampleResponse());

            adapter.reserve(1L, 5, "Bearer user-token");

            verify(productFeign).reserve(eq(1L), eq(new StockAdjustmentRequestDto(5)), eq("Bearer user-token"));
        }

        @Test
        void throwsNotFoundWhenProductMissing() {
            when(productFeign.reserve(eq(1L), eq(new StockAdjustmentRequestDto(5)), anyString())).thenThrow(feignException(404, null));

            assertThatThrownBy(() -> adapter.reserve(1L, 5, null)).isInstanceOf(NotFoundException.class);
        }

        @Test
        void throwsBusinessRuleOnOtherFeignFailures() {
            when(productFeign.reserve(eq(1L), eq(new StockAdjustmentRequestDto(5)), anyString()))
                    .thenThrow(feignException(422, "{\"detail\":\"Estoque insuficiente\"}"));

            assertThatThrownBy(() -> adapter.reserve(1L, 5, null))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("Estoque insuficiente");
        }
    }

    @Nested
    class Release {

        @Test
        void callsFeignWithoutThrowingOnSuccess() {
            adapter.release(1L, 3, null);

            verify(productFeign).release(eq(1L), eq(new StockAdjustmentRequestDto(3)), anyString());
        }

        @Test
        void relaysTheCallerTokenWhenGiven() {
            adapter.release(1L, 3, "Bearer user-token");

            verify(productFeign).release(eq(1L), eq(new StockAdjustmentRequestDto(3)), eq("Bearer user-token"));
        }

        @Test
        void swallowsNotFoundAndLogsInstead() {
            when(productFeign.release(eq(1L), eq(new StockAdjustmentRequestDto(3)), anyString())).thenThrow(feignException(404, null));

            assertThatCode(() -> adapter.release(1L, 3, null)).doesNotThrowAnyException();

            verify(productFeign).release(eq(1L), eq(new StockAdjustmentRequestDto(3)), anyString());
        }

        @Test
        void swallowsAnyOtherFeignFailureAndLogsInstead() {
            when(productFeign.release(eq(1L), eq(new StockAdjustmentRequestDto(3)), anyString())).thenThrow(feignException(503, null));

            assertThatCode(() -> adapter.release(1L, 3, null)).doesNotThrowAnyException();

            verify(productFeign).release(eq(1L), eq(new StockAdjustmentRequestDto(3)), anyString());
        }
    }

    @Nested
    class Confirm {

        @Test
        void callsFeignWithoutThrowingOnSuccess() {
            adapter.confirm(1L, 2, null);

            verify(productFeign).confirm(eq(1L), eq(new StockAdjustmentRequestDto(2)), anyString());
        }

        @Test
        void relaysTheCallerTokenWhenGiven() {
            adapter.confirm(1L, 2, "Bearer user-token");

            verify(productFeign).confirm(eq(1L), eq(new StockAdjustmentRequestDto(2)), eq("Bearer user-token"));
        }

        @Test
        void swallowsFeignFailureAndLogsInstead() {
            when(productFeign.confirm(eq(1L), eq(new StockAdjustmentRequestDto(2)), anyString())).thenThrow(feignException(503, null));

            assertThatCode(() -> adapter.confirm(1L, 2, null)).doesNotThrowAnyException();

            verify(productFeign).confirm(eq(1L), eq(new StockAdjustmentRequestDto(2)), anyString());
        }
    }
}
