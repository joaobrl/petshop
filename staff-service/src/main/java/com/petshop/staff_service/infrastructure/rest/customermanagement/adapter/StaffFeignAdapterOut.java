package com.petshop.staff_service.infrastructure.rest.customermanagement.adapter;

import com.petshop.staff_service.core.domain.Staff;
import com.petshop.staff_service.core.port.out.StaffPortOut;
import com.petshop.staff_service.infrastructure.rest.customermanagement.feign.StaffRegistryFeign;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class StaffFeignAdapterOut implements StaffPortOut {

    private final StaffRegistryFeign staffRegistryFeign;

    @Override
    public List<Staff> findAll() {
        return staffRegistryFeign.findAll().stream()
                .map(dto -> new Staff(dto.getId(), dto.getName(), dto.getCpf(), dto.getEmail(), dto.getPhone(), dto.getEnabled(), dto.getRole()))
                .toList();
    }
}
