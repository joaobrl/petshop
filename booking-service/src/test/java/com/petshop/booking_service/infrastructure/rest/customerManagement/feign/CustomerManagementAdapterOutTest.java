package com.petshop.booking_service.infrastructure.rest.customerManagement.feign;

import com.petshop.booking_service.infrastructure.rest.customerManagement.feign.dto.StaffDto;
import com.petshop.booking_service.infrastructure.rest.customerManagement.feign.mapper.CustomerMapper;
import com.petshop.booking_service.infrastructure.security.TokenPropagation;
import com.petshop.commons.security.jwt.JwtService;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerManagementAdapterOutTest {

    private static final JwtService JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Mock
    private CustomerManagementFeign feignClient;

    private CustomerManagementAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new CustomerManagementAdapterOut(feignClient, new CustomerMapper(), new TokenPropagation(JWT_SERVICE));
    }

    @Test
    void mapsFeignResponseToStaffAssignments() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var id = UUID.randomUUID();
        when(feignClient.getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), anyString()))
                .thenReturn(List.of(new StaffDto(id, "Ciclana")));

        var result = adapter.getScheduledStaff(dateTime, "GROOMER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(id);
        assertThat(result.get(0).name()).isEqualTo("Ciclana");
    }

    @Test
    void alwaysUsesAFreshServiceTokenNeverARelayedOne() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        when(feignClient.getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), anyString()))
                .thenReturn(List.of());

        adapter.getScheduledStaff(dateTime, "GROOMER");

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(feignClient).getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), captor.capture());
        var parsed = JWT_SERVICE.parse(captor.getValue().substring("Bearer ".length()));
        assertThat(parsed.type().name()).isEqualTo("SERVICE");
    }

    @Test
    void returnsEmptyListWhenFeignReturnsNull() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        when(feignClient.getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), anyString())).thenReturn(null);

        assertThat(adapter.getScheduledStaff(dateTime, "GROOMER")).isEmpty();
    }

    @Test
    void returnsEmptyListWhenStaffServiceIsUnavailable() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var request = Request.create(Request.HttpMethod.GET, "/api/v1/availability/schedule",
                Map.of(), null, StandardCharsets.UTF_8, new RequestTemplate());
        when(feignClient.getSchedule(eq("15/08/2026 09:00"), eq("GROOMER"), anyString()))
                .thenThrow(new FeignException.ServiceUnavailable("unavailable", request, null, null));

        assertThat(adapter.getScheduledStaff(dateTime, "GROOMER")).isEmpty();
    }
}
