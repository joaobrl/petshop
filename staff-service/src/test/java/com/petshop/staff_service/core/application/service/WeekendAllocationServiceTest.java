package com.petshop.staff_service.core.application.service;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.security.Role;
import com.petshop.staff_service.core.domain.Staff;
import com.petshop.staff_service.core.domain.WeekendAllocation;
import com.petshop.staff_service.core.port.out.StaffPortOut;
import com.petshop.staff_service.core.port.out.WeekendAllocationPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeekendAllocationServiceTest {

    @Mock
    private StaffPortOut staffPortOut;
    @Mock
    private WeekendAllocationPortOut weekendAllocationPortOut;

    private WeekendAllocationService service;

    @BeforeEach
    void setUp() {
        service = new WeekendAllocationService(staffPortOut, weekendAllocationPortOut);
        ReflectionTestUtils.setField(service, "saturdayStaffCount", 2);
    }

    private Staff groomer(String name, UUID id) {
        return new Staff(id, name, "12345678900", name + "@petshop.com", "11999990000", true, Role.GROOMER);
    }

    @Test
    void throwsBusinessRuleExceptionWhenNotEnoughEnabledGroomers() {
        when(staffPortOut.findAll()).thenReturn(List.of(groomer("Unica", UUID.randomUUID())));

        assertThatThrownBy(() -> service.generateNextMonth())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("mínimo: 2")
                .hasMessageContaining("cadastrados e habilitados: 1");

        verify(weekendAllocationPortOut, org.mockito.Mockito.never()).saveAll(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresDisabledAndNonGroomerStaffWhenCountingAvailability() {
        when(staffPortOut.findAll()).thenReturn(List.of(
                groomer("Habilitada", UUID.randomUUID()),
                new Staff(UUID.randomUUID(), "Desabilitada", "1", "d@x.com", "1", false, Role.GROOMER),
                new Staff(UUID.randomUUID(), "OutroCargo", "1", "o@x.com", "1", true, Role.VETERINARIAN)
        ));

        assertThatThrownBy(() -> service.generateNextMonth())
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("cadastrados e habilitados: 1");
    }

    @Test
    void generatesAllocationsForEverySaturdayOfNextMonthAfterLastAllocatedDate() {
        var g1 = groomer("Ana", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var g2 = groomer("Bruno", UUID.fromString("00000000-0000-0000-0000-000000000002"));
        var g3 = groomer("Carla", UUID.fromString("00000000-0000-0000-0000-000000000003"));
        when(staffPortOut.findAll()).thenReturn(List.of(g3, g1, g2));
        when(weekendAllocationPortOut.findLastAllocatedDate()).thenReturn(Optional.of(LocalDate.of(2026, 7, 31)));
        when(weekendAllocationPortOut.countAll()).thenReturn(0L);
        when(weekendAllocationPortOut.saveAll(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.generateNextMonth();

        assertThat(result).hasSize(10);
        var groomerIds = List.of(g1.getId(), g2.getId(), g3.getId());
        assertThat(result).allSatisfy(allocation -> assertThat(groomerIds).contains(allocation.getStaffId()));

        var distinctDates = result.stream().map(WeekendAllocation::getAllocationDate).collect(Collectors.toSet());
        assertThat(distinctDates).containsExactlyInAnyOrder(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 8), LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 8, 22), LocalDate.of(2026, 8, 29)
        );

        for (LocalDate saturday : distinctDates) {
            long countForDate = result.stream().filter(a -> a.getAllocationDate().equals(saturday)).count();
            assertThat(countForDate).isEqualTo(2);
        }
    }

    @Test
    void usesCurrentMonthWhenNoAllocationExistsYet() {
        var g1 = groomer("Ana", UUID.randomUUID());
        var g2 = groomer("Bruno", UUID.randomUUID());
        when(staffPortOut.findAll()).thenReturn(List.of(g1, g2));
        when(weekendAllocationPortOut.findLastAllocatedDate()).thenReturn(Optional.empty());
        when(weekendAllocationPortOut.countAll()).thenReturn(0L);
        when(weekendAllocationPortOut.saveAll(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.generateNextMonth();

        assertThat(result).isNotEmpty();
        var currentMonth = YearMonth.now();
        assertThat(result).allSatisfy(allocation ->
                assertThat(YearMonth.from(allocation.getAllocationDate())).isEqualTo(currentMonth));
    }

    @Test
    void roundRobinCursorContinuesFromExistingAllocationCount() {
        var g1 = groomer("Ana", UUID.fromString("00000000-0000-0000-0000-000000000001"));
        var g2 = groomer("Bruno", UUID.fromString("00000000-0000-0000-0000-000000000002"));
        when(staffPortOut.findAll()).thenReturn(List.of(g2, g1));
        when(weekendAllocationPortOut.findLastAllocatedDate()).thenReturn(Optional.of(LocalDate.of(2026, 7, 31)));
        when(weekendAllocationPortOut.countAll()).thenReturn(1L);
        ArgumentCaptor<List<WeekendAllocation>> captor = ArgumentCaptor.forClass(List.class);
        when(weekendAllocationPortOut.saveAll(captor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.generateNextMonth();

        var firstAllocation = captor.getValue().get(0);
        assertThat(firstAllocation.getStaffId()).isEqualTo(g2.getId());
    }
}