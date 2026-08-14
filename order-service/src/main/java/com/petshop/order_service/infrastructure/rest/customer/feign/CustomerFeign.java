package com.petshop.order_service.infrastructure.rest.customer.feign;

import com.petshop.order_service.infrastructure.rest.customer.feign.dto.CustomerInfoResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "customer-management", url = "${external.api.customer-service.url}")
public interface CustomerFeign {

    @GetMapping("/api/v1/customers/find/customer/{id}")
    CustomerInfoResponseDto getCustomerById(@PathVariable("id") UUID id);
}
