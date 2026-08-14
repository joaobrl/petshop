package com.petshop.booking_service.infrastructure.rest.customerManagement.feign;

import com.petshop.booking_service.infrastructure.rest.customerManagement.feign.dto.AvailabilityDTO;
import com.petshop.booking_service.infrastructure.rest.customerManagement.feign.dto.StaffDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "staff-service", url = "${external.api.staff-service.url}")
public interface CustomerManagementFeign {

    @GetMapping("/api/v1/availability/general")
    AvailabilityDTO getGeneralAvailability(
            @RequestParam("dateTime") String dateTime,
            @RequestParam("role") String role
    );

    @GetMapping("/api/v1/availability/schedule")
    List<StaffDto> getSchedule(
            @RequestParam("dateTime") String dateTime,
            @RequestParam("role") String role
    );

}
