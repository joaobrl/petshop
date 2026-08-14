package com.petshop.staff_service.core.application.service;

import com.petshop.commons.security.Role;
import com.petshop.staff_service.core.domain.Staff;
import com.petshop.staff_service.core.domain.WeekendAllocation;
import com.petshop.staff_service.core.port.out.StaffPortOut;
import com.petshop.staff_service.core.port.out.WeekendAllocationPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Datas âncora: 10/08/2026 é segunda-feira (data corrente do ambiente),
 * então 15/08/2026 é sábado e 16/08/2026 é domingo — usadas em todos os
 * cenários pra evitar depender do calendário do dia em que os testes rodam.
 */
@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 10);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 8, 15);
    private static final LocalDate SUNDAY = LocalDate.of(2026, 8, 16);

    @Mock
    private StaffPortOut staffPortOut;
    @Mock
    private WeekendAllocationPortOut weekendAllocationPortOut;

    private AvailabilityService service;

    @BeforeEach
    void setUp() {
        service = new AvailabilityService(staffPortOut, weekendAllocationPortOut);
    }

    private Staff staff(String name, boolean enabled, Role role) {
        return new Staff(UUID.randomUUID(), name, "12345678900", name + "@petshop.com", "11999990000", enabled, role);
    }

    @Nested
    class CheckGeneralAvailability {

        @Test
        void rejectsNullDateTime() {
            assertThatThrownBy(() -> service.checkGeneralAvailability(null, Role.GROOMER))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("dateTime é obrigatório");
        }

        @Test
        void rejectsNullRole() {
            var dateTime = MONDAY.atTime(9, 0);
            assertThatThrownBy(() -> service.checkGeneralAvailability(dateTime, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("role é obrigatório");
        }

        @Test
        void returnsUnavailableForInvalidSlotWithoutQueryingStaff() {
            var dateTime = SUNDAY.atTime(9, 0);

            var result = service.checkGeneralAvailability(dateTime, Role.GROOMER);

            assertThat(result.getAvailableSlots()).isZero();
            assertThat(result.isAvailable()).isFalse();
            assertThat(result.getValidatedDateTime()).isEqualTo(dateTime);
        }

        @Test
        void returnsCapacityFromEnabledStaffOfRoleOnValidWeekdaySlot() {
            var dateTime = MONDAY.atTime(9, 0);
            when(staffPortOut.findAll()).thenReturn(List.of(
                    staff("Ciclana", true, Role.GROOMER),
                    staff("Beltrano", true, Role.GROOMER),
                    staff("Fulano", true, Role.VETERINARIAN)
            ));

            var result = service.checkGeneralAvailability(dateTime, Role.GROOMER);

            assertThat(result.getAvailableSlots()).isEqualTo(2);
            assertThat(result.isAvailable()).isTrue();
        }

        @Test
        void returnsUnavailableWhenNoEnabledStaffOfRole() {
            var dateTime = MONDAY.atTime(9, 0);
            when(staffPortOut.findAll()).thenReturn(List.of(staff("Ciclana", false, Role.GROOMER)));

            var result = service.checkGeneralAvailability(dateTime, Role.GROOMER);

            assertThat(result.getAvailableSlots()).isZero();
            assertThat(result.isAvailable()).isFalse();
        }
    }

    @Nested
    class ScheduledStaffFor {

        @Test
        void returnsEmptyForNullDateTime() {
            assertThat(service.scheduledStaffFor(null, Role.GROOMER)).isEmpty();
        }

        @Test
        void returnsEmptyForNullRole() {
            assertThat(service.scheduledStaffFor(MONDAY.atTime(9, 0), null)).isEmpty();
        }

        @Test
        void returnsEmptyForInvalidSlot() {
            assertThat(service.scheduledStaffFor(SUNDAY.atTime(9, 0), Role.GROOMER)).isEmpty();
        }

        @Test
        void filtersByEnabledAndRoleOnWeekdays() {
            var dateTime = MONDAY.atTime(9, 0);
            var enabledGroomer = staff("Ciclana", true, Role.GROOMER);
            when(staffPortOut.findAll()).thenReturn(List.of(
                    enabledGroomer,
                    staff("Desabilitada", false, Role.GROOMER),
                    staff("OutroCargo", true, Role.VETERINARIAN)
            ));

            var result = service.scheduledStaffFor(dateTime, Role.GROOMER);

            assertThat(result).containsExactly(enabledGroomer);
        }

        @Test
        void returnsEmptyWhenNoEnabledStaffOfRoleExists() {
            var dateTime = MONDAY.atTime(9, 0);
            when(staffPortOut.findAll()).thenReturn(List.of());

            assertThat(service.scheduledStaffFor(dateTime, Role.GROOMER)).isEmpty();
        }

        @Test
        void onSaturdayOnlyReturnsStaffAllocatedForThatDate() {
            var dateTime = SATURDAY.atTime(9, 0);
            var allocated = staff("Alocada", true, Role.GROOMER);
            var notAllocated = staff("NaoAlocada", true, Role.GROOMER);
            when(staffPortOut.findAll()).thenReturn(List.of(allocated, notAllocated));
            when(weekendAllocationPortOut.findByDate(SATURDAY))
                    .thenReturn(List.of(new WeekendAllocation(SATURDAY, allocated)));

            var result = service.scheduledStaffFor(dateTime, Role.GROOMER);

            assertThat(result).containsExactly(allocated);
        }

        @Test
        void onSaturdayFiltersOutAllocationsForStaffNoLongerEnabled() {
            var dateTime = SATURDAY.atTime(9, 0);
            var disabledButAllocated = staff("Desabilitada", false, Role.GROOMER);
            // enabled=false já é filtrado antes de olhar a Alocação Final de Semana,
            // então findByDate nunca seria chamado (ver enabledStaffOfRole.isEmpty()).
            when(staffPortOut.findAll()).thenReturn(List.of(disabledButAllocated));

            var result = service.scheduledStaffFor(dateTime, Role.GROOMER);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    class SlotValidation {

        @ParameterizedTest(name = "{0} às {1} é slot válido = {2}")
        @CsvSource({
                "09:00, true",
                "11:30, true",
                "12:00, false",
                "12:30, false",
                "13:00, true",
                "16:30, true",
                "17:00, false",
                "08:30, false"
        })
        void validatesWeekdaySlots(String time, boolean expectedValid) {
            var dateTime = MONDAY.atTime(java.time.LocalTime.parse(time));
            lenient().when(staffPortOut.findAll()).thenReturn(List.of(staff("Ciclana", true, Role.GROOMER)));

            var result = service.checkGeneralAvailability(dateTime, Role.GROOMER);

            assertThat(result.isAvailable()).isEqualTo(expectedValid);
        }

        @Test
        void rejectsMinuteNotAlignedToSlotGranularity() {
            var dateTime = MONDAY.atTime(9, 15);

            var result = service.checkGeneralAvailability(dateTime, Role.GROOMER);

            assertThat(result.isAvailable()).isFalse();
        }

        @Test
        void rejectsNonZeroSeconds() {
            var dateTime = LocalDateTime.of(2026, 8, 10, 9, 0, 15);

            var result = service.checkGeneralAvailability(dateTime, Role.GROOMER);

            assertThat(result.isAvailable()).isFalse();
        }

        @ParameterizedTest(name = "sábado {0} com GROOMER é slot válido = {1}")
        @CsvSource({
                "08:00, true",
                "10:30, true",
                "11:00, false",
                "07:30, false"
        })
        void validatesSaturdaySlotsForGroomer(String time, boolean expectedValid) {
            var dateTime = SATURDAY.atTime(java.time.LocalTime.parse(time));
            var ciclana = staff("Ciclana", true, Role.GROOMER);
            lenient().when(staffPortOut.findAll()).thenReturn(List.of(ciclana));
            // Aloca Ciclana nesse sábado pra isolar a validação de horário (o que este
            // teste cobre) da regra "sem alocação = ninguém disponível" (coberta acima).
            lenient().when(weekendAllocationPortOut.findByDate(SATURDAY))
                    .thenReturn(List.of(new WeekendAllocation(SATURDAY, ciclana)));

            var result = service.checkGeneralAvailability(dateTime, Role.GROOMER);

            assertThat(result.isAvailable()).isEqualTo(expectedValid);
        }

        @Test
        void rejectsSaturdayForNonGroomerRole() {
            var dateTime = SATURDAY.atTime(9, 0);

            var result = service.checkGeneralAvailability(dateTime, Role.VETERINARIAN);

            assertThat(result.isAvailable()).isFalse();
        }

        @Test
        void rejectsSundayRegardlessOfTime() {
            var dateTime = SUNDAY.atTime(10, 0);

            var result = service.checkGeneralAvailability(dateTime, Role.GROOMER);

            assertThat(result.isAvailable()).isFalse();
        }
    }
}
