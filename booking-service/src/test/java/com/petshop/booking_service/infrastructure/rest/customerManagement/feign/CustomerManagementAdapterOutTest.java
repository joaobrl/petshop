package com.petshop.booking_service.infrastructure.rest.customerManagement.feign;

import com.petshop.booking_service.infrastructure.rest.customerManagement.feign.dto.StaffDto;
import com.petshop.booking_service.infrastructure.rest.customerManagement.feign.mapper.CustomerMapper;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerManagementAdapterOutTest {

    @Mock
    private CustomerManagementFeign feignClient;

    private CustomerManagementAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new CustomerManagementAdapterOut(feignClient, new CustomerMapper());
    }

    @Test
    void mapsFeignResponseToStaffAssignments() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var id = UUID.randomUUID();
        when(feignClient.getSchedule("15/08/2026 09:00", "GROOMER"))
                .thenReturn(List.of(new StaffDto(id, "Ciclana")));

        var result = adapter.getScheduledStaff(dateTime, "GROOMER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id);
        assertThat(result.get(0).name()).isEqualTo("Ciclana");
    }

    @Test
    void returnsEmptyListWhenFeignReturnsNull() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        when(feignClient.getSchedule("15/08/2026 09:00", "GROOMER")).thenReturn(null);

        assertThat(adapter.getScheduledStaff(dateTime, "GROOMER")).isEmpty();
    }

    @Test
    void returnsEmptyListWhenStaffServiceIsUnavailable() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var request = Request.create(Request.HttpMethod.GET, "/api/v1/availability/schedule",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        when(feignClient.getSchedule("15/08/2026 09:00", "GROOMER"))
                .thenThrow(new FeignException.ServiceUnavailable("unavailable", request, null, null));

        assertThat(adapter.getScheduledStaff(dateTime, "GROOMER")).isEmpty();
    }
}
