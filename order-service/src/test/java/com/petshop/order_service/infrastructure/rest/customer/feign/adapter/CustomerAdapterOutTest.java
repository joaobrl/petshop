package com.petshop.order_service.infrastructure.rest.customer.feign.adapter;

import com.petshop.order_service.infrastructure.rest.customer.feign.CustomerFeign;
import com.petshop.order_service.infrastructure.rest.customer.feign.dto.CustomerInfoResponseDto;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAdapterOutTest {

    @Mock
    private CustomerFeign customerFeign;

    private CustomerAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new CustomerAdapterOut(customerFeign);
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
        when(customerFeign.getCustomerById(id)).thenReturn(new CustomerInfoResponseDto(id, "Maria", "maria@mail.com"));

        var result = adapter.findCustomerById(id);

        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(id);
        assertThat(result.get().name()).isEqualTo("Maria");
        assertThat(result.get().email()).isEqualTo("maria@mail.com");
    }

    @Test
    void returnsEmptyWhenFeignReturnsNull() {
        var id = UUID.randomUUID();
        when(customerFeign.getCustomerById(id)).thenReturn(null);

        assertThat(adapter.findCustomerById(id)).isEmpty();
    }

    @Test
    void returnsEmptyWhenCustomerNotFound() {
        var id = UUID.randomUUID();
        when(customerFeign.getCustomerById(id)).thenThrow(feignException(404));

        assertThat(adapter.findCustomerById(id)).isEmpty();
    }

    @Test
    void returnsEmptyAndLogsWhenFeignFailsForAnyOtherReason() {
        var id = UUID.randomUUID();
        when(customerFeign.getCustomerById(id)).thenThrow(feignException(503));

        assertThat(adapter.findCustomerById(id)).isEmpty();
    }
}
