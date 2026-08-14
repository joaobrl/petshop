package com.petshop.staff_service.core.domain;

import com.petshop.commons.security.Role;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WeekendAllocationTest {

    @Test
    void staffConstructorCopiesIdAndName() {
        var staffId = UUID.randomUUID();
        var staff = new Staff(staffId, "Ciclana", "12345678900", "ciclana@petshop.com", "11999990000", true, Role.GROOMER);
        var saturday = LocalDate.of(2026, 8, 15);

        var allocation = new WeekendAllocation(saturday, staff);

        assertThat(allocation.getAllocationDate()).isEqualTo(saturday);
        assertThat(allocation.getStaffId()).isEqualTo(staffId);
        assertThat(allocation.getStaffName()).isEqualTo("Ciclana");
        assertThat(allocation.getId()).isNull();
    }

    @Test
    void allArgsConstructorAndSettersRoundTrip() {
        var id = UUID.randomUUID();
        var staffId = UUID.randomUUID();
        var saturday = LocalDate.of(2026, 8, 22);

        var allocation = new WeekendAllocation(id, saturday, staffId, "Beltrano");

        assertThat(allocation.getId()).isEqualTo(id);
        assertThat(allocation.getAllocationDate()).isEqualTo(saturday);
        assertThat(allocation.getStaffId()).isEqualTo(staffId);
        assertThat(allocation.getStaffName()).isEqualTo("Beltrano");

        var otherDate = LocalDate.of(2026, 9, 5);
        allocation.setAllocationDate(otherDate);
        allocation.setStaffName("Outro Nome");
        assertThat(allocation.getAllocationDate()).isEqualTo(otherDate);
        assertThat(allocation.getStaffName()).isEqualTo("Outro Nome");
    }

    @Test
    void noArgsConstructorCreatesEmptyInstance() {
        var allocation = new WeekendAllocation();

        assertThat(allocation.getId()).isNull();
        assertThat(allocation.getAllocationDate()).isNull();
        assertThat(allocation.getStaffId()).isNull();
        assertThat(allocation.getStaffName()).isNull();
    }
}
