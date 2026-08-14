package com.petshop.staff_service.infrastructure.rest.customermanagement.feign;

import com.petshop.staff_service.infrastructure.rest.customermanagement.dto.StaffRegistryResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@FeignClient(name = "customer-management", url = "${external.api.customer-management.url}")
public interface StaffRegistryFeign {

    @GetMapping("/api/v1/staff/all")
    List<StaffRegistryResponseDto> findAll();
}
