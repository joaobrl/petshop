package com.petshop.staff_service.api.rest.dto;

import com.petshop.staff_service.core.domain.Staff;
import com.petshop.staff_service.core.domain.WeekendAllocation;
import com.petshop.commons.security.Role;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WeekendAllocationResponseDtoTest {

    @Test
    void mapsFromWeekendAllocation() {
        var staffId = UUID.randomUUID();
        var staff = new Staff(staffId, "Ciclana", "12345678900", "ciclana@petshop.com", "11999990000", true, Role.GROOMER);
        var saturday = LocalDate.of(2026, 8, 15);
        var allocation = new WeekendAllocation(saturday, staff);

        var dto = new WeekendAllocationResponseDto(allocation);

        assertThat(dto.getAllocationDate()).isEqualTo(saturday);
        assertThat(dto.getStaffId()).isEqualTo(staffId);
        assertThat(dto.getStaffName()).isEqualTo("Ciclana");
    }

    @Test
    void settersRoundTrip() {
        var dto = new WeekendAllocationResponseDto();
        var staffId = UUID.randomUUID();
        var date = LocalDate.of(2026, 9, 5);

        dto.setAllocationDate(date);
        dto.setStaffId(staffId);
        dto.setStaffName("Beltrano");

        assertThat(dto.getAllocationDate()).isEqualTo(date);
        assertThat(dto.getStaffId()).isEqualTo(staffId);
        assertThat(dto.getStaffName()).isEqualTo("Beltrano");
    }

    @Test
    void equalsAndHashCodeConsiderAllFields() {
        var staffId = UUID.randomUUID();
        var staff = new Staff(staffId, "Ciclana", "12345678900", "ciclana@petshop.com", "11999990000", true, Role.GROOMER);
        var saturday = LocalDate.of(2026, 8, 15);

        var a = new WeekendAllocationResponseDto(new WeekendAllocation(saturday, staff));
        var b = new WeekendAllocationResponseDto(new WeekendAllocation(saturday, staff));

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("staffName");
    }
}
