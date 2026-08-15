package com.petshop.staff_service.infrastructure.rest.customermanagement.feign;

import com.petshop.commons.dto.PageResponse;
import com.petshop.staff_service.infrastructure.rest.customermanagement.dto.StaffRegistryResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "customer-management", url = "${external.api.customer-management.url}")
public interface StaffRegistryFeign {

    @GetMapping("/api/v1/staff/all")
    PageResponse<StaffRegistryResponseDto> findAll(@RequestParam("page") int page, @RequestParam("size") int size);
}
