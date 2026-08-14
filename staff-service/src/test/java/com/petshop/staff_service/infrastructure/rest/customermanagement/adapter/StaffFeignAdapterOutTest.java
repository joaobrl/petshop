package com.petshop.staff_service.infrastructure.rest.customermanagement.adapter;

import com.petshop.commons.dto.PageResponse;
import com.petshop.commons.security.Role;
import com.petshop.staff_service.infrastructure.rest.customermanagement.dto.StaffRegistryResponseDto;
import com.petshop.staff_service.infrastructure.rest.customermanagement.feign.StaffRegistryFeign;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffFeignAdapterOutTest {

    @Mock
    private StaffRegistryFeign staffRegistryFeign;

    private StaffFeignAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new StaffFeignAdapterOut(staffRegistryFeign);
    }

    @Test
    void mapsFeignDtosToDomainStaff() {
        var id = UUID.randomUUID();
        var dto = new StaffRegistryResponseDto();
        dto.setId(id);
        dto.setName("Ciclana");
        dto.setCpf("12345678900");
        dto.setEmail("ciclana@petshop.com");
        dto.setPhone("11999990000");
        dto.setEnabled(true);
        dto.setRole(Role.GROOMER);
        when(staffRegistryFeign.findAll(0, 500)).thenReturn(new PageResponse<>(List.of(dto), 0, 500, 1, 1, true));

        var result = adapter.findAll();

        assertThat(result).hasSize(1);
        var staff = result.get(0);
        assertThat(staff.getId()).isEqualTo(id);
        assertThat(staff.getName()).isEqualTo("Ciclana");
        assertThat(staff.getCpf()).isEqualTo("12345678900");
        assertThat(staff.getEmail()).isEqualTo("ciclana@petshop.com");
        assertThat(staff.getPhone()).isEqualTo("11999990000");
        assertThat(staff.getEnabled()).isTrue();
        assertThat(staff.getRole()).isEqualTo(Role.GROOMER);
    }

    @Test
    void returnsEmptyListWhenFeignReturnsNoStaff() {
        when(staffRegistryFeign.findAll(0, 500)).thenReturn(new PageResponse<>(List.of(), 0, 500, 0, 0, true));

        assertThat(adapter.findAll()).isEmpty();
    }
}
