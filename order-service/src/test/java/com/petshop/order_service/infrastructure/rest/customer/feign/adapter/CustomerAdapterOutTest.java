package com.petshop.order_service.infrastructure.rest.customer.feign.adapter;

import com.petshop.commons.security.jwt.JwtService;
import com.petshop.order_service.infrastructure.rest.customer.feign.CustomerFeign;
import com.petshop.order_service.infrastructure.rest.customer.feign.dto.CustomerInfoResponseDto;
import com.petshop.order_service.infrastructure.security.TokenPropagation;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAdapterOutTest {

    private static final JwtService JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Mock
    private CustomerFeign customerFeign;

    private CustomerAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new CustomerAdapterOut(customerFeign, new TokenPropagation(JWT_SERVICE));
    }

    private FeignException feignException(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/v1/customers/find/customer/1",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
        Response response = Response.builder()
                .status(status)
                .reason("reason")
                .request(request)
                .headers(Collections.emptyMap())
                .body(new byte[0])
                .build();
        return FeignException.errorStatus("CustomerFeign#getCustomerById(UUID)", response);
    }

    @Test
    void returnsMappedCustomerInfoWhenFound() {
        var id = UUID.randomUUID();
        when(customerFeign.getCustomerById(eq(id), anyString())).thenReturn(new CustomerInfoResponseDto(id, "Maria", "maria@mail.com"));

        var result = adapter.findCustomerById(id, "Bearer user-token");

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(id);
        assertThat(result.get().name()).isEqualTo("Maria");
        assertThat(result.get().email()).isEqualTo("maria@mail.com");
    }

    @Test
    void relaysTheGivenTokenToFeign() {
        var id = UUID.randomUUID();
        when(customerFeign.getCustomerById(eq(id), anyString())).thenReturn(new CustomerInfoResponseDto(id, "Maria", "maria@mail.com"));

        adapter.findCustomerById(id, "Bearer user-token");

        verify(customerFeign).getCustomerById(id, "Bearer user-token");
    }

    @Test
    void mintsAServiceTokenWhenNoTokenIsGiven() {
        var id = UUID.randomUUID();
        when(customerFeign.getCustomerById(eq(id), anyString())).thenReturn(new CustomerInfoResponseDto(id, "Maria", "maria@mail.com"));

        var result = adapter.findCustomerById(id, null);

        assertThat(result).isPresent();
    }

    @Test
    void returnsEmptyWhenFeignReturnsNull() {
        var id = UUID.randomUUID();
        when(customerFeign.getCustomerById(eq(id), anyString())).thenReturn(null);

        assertThat(adapter.findCustomerById(id, "Bearer user-token")).isEmpty();
    }

    @Test
    void returnsEmptyWhenCustomerNotFound() {
        var id = UUID.randomUUID();
        when(customerFeign.getCustomerById(eq(id), anyString())).thenThrow(feignException(404));

        assertThat(adapter.findCustomerById(id, "Bearer user-token")).isEmpty();
    }

    @Test
    void returnsEmptyAndLogsWhenFeignFailsForAnyOtherReason() {
        var id = UUID.randomUUID();
        when(customerFeign.getCustomerById(eq(id), anyString())).thenThrow(feignException(503));

        assertThat(adapter.findCustomerById(id, "Bearer user-token")).isEmpty();
    }
}
