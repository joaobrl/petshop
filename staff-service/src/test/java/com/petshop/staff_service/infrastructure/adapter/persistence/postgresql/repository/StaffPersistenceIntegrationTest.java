package com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.repository;

import com.petshop.commons.security.Role;
import com.petshop.staff_service.core.domain.Staff;
import com.petshop.staff_service.infrastructure.adapter.persistence.postgresql.entity.WeekendAllocationEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StaffPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");

    @Autowired
    private WeekendAllocationRepository weekendAllocationRepository;

    private Staff sampleStaff(String name, String cpf) {
        return new Staff(UUID.randomUUID(), name, cpf, name.toLowerCase() + "@petshop.com",
                "11999990000", true, Role.GROOMER);
    }

    private WeekendAllocationEntity sampleAllocationEntity(LocalDate allocationDate, Staff staff) {
        var entity = new WeekendAllocationEntity();
        entity.setAllocationDate(allocationDate);
        entity.setStaffId(staff.getId());
        entity.setStaffName(staff.getName());
        return entity;
    }

    @Test
    void findsLastAllocatedDateAmongMultipleSaturdays() {
        var staff = sampleStaff("Ciclana", "98765432100");

        weekendAllocationRepository.save(sampleAllocationEntity(LocalDate.of(2026, 8, 1), staff));
        weekendAllocationRepository.save(sampleAllocationEntity(LocalDate.of(2026, 8, 15), staff));
        weekendAllocationRepository.save(sampleAllocationEntity(LocalDate.of(2026, 8, 8), staff));

        var lastDate = weekendAllocationRepository.findLastAllocatedDate();

        assertThat(lastDate).contains(LocalDate.of(2026, 8, 15));
    }

    @Test
    void findLastAllocatedDateReturnsEmptyWhenNothingAllocatedYet() {
        var lastDate = weekendAllocationRepository.findLastAllocatedDate();

        assertThat(lastDate).isEmpty();
    }

    @Test
    void findsAllocationsForASpecificSaturday() {
        var staff = sampleStaff("Beltrana", "11122233344");
        var saturday = LocalDate.of(2026, 8, 1);
        weekendAllocationRepository.save(sampleAllocationEntity(saturday, staff));

        var allocations = weekendAllocationRepository.findByAllocationDate(saturday);

        assertThat(allocations).hasSize(1);
        assertThat(allocations.get(0).getStaffName()).isEqualTo("Beltrana");
    }

    @Test
    void findByAllocationDateReturnsEmptyWhenNoneMatch() {
        var allocations = weekendAllocationRepository.findByAllocationDate(LocalDate.of(2026, 8, 1));

        assertThat(allocations).isEmpty();
    }
}
