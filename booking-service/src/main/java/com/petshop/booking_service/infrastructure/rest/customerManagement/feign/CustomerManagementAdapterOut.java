package com.petshop.booking_service.infrastructure.rest.customerManagement.feign;

import com.petshop.booking_service.core.domain.StaffAssignment;
import com.petshop.booking_service.core.port.out.CustomerManagementPortOut;
import com.petshop.booking_service.infrastructure.rest.customerManagement.feign.mapper.CustomerMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerManagementAdapterOut implements CustomerManagementPortOut {

    private final CustomerManagementFeign feignClient;
    private final CustomerMapper mapper;

    @Override
    public List<StaffAssignment> getScheduledStaff(LocalDateTime dateTime, String role) {
        String formattedDate = mapper.toQueryParam(dateTime);

        try {
            var response = feignClient.getSchedule(formattedDate, role);

            if (response == null) {
                log.warn("MS staff-service devolveu resposta vazia pra data: {}", formattedDate);
                return List.of();
            }

            return response.stream()
                    .map(staff -> new StaffAssignment(staff.id(), staff.name()))
                    .toList();

        } catch (FeignException e) {
            log.error("Falha ao consultar MS staff-service. Status: {}. Erro: {}", e.status(), e.getMessage());
            return List.of();
        }
    }
}
