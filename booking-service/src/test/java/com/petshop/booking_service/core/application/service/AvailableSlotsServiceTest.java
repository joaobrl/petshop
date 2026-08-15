package com.petshop.booking_service.core.application.service;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.StaffAssignment;
import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.port.out.BookingPortOut;
import com.petshop.booking_service.core.port.out.CustomerManagementPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailableSlotsServiceTest {

    private static final LocalDate FAR_FUTURE_MONDAY =
            LocalDate.now().plusYears(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private static final LocalDate FAR_FUTURE_SATURDAY =
            FAR_FUTURE_MONDAY.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
    private static final LocalDate FAR_FUTURE_SUNDAY =
            FAR_FUTURE_MONDAY.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));

    @Mock
    private BookingPortOut bookingPortOut;
    @Mock
    private CustomerManagementPortOut customerManagementPortOut;

    private AvailableSlotsService service;

    @BeforeEach
    void setUp() {
        service = new AvailableSlotsService(bookingPortOut, customerManagementPortOut);
    }

    private StaffAssignment staff(String name) {
        return new StaffAssignment(UUID.randomUUID(), name);
    }

    @Test
    void rejectsNullServiceType() {
        assertThatThrownBy(() -> service.findAvailableSlots(null, FAR_FUTURE_MONDAY, RangeType.DAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tipo de serviço não pode ser nulo");
    }

    @Test
    void rejectsNullReferenceDate() {
        assertThatThrownBy(() -> service.findAvailableSlots(ServiceType.BANHO, null, RangeType.DAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Data de referência não pode ser nula");
    }

    @Test
    void rejectsNullRange() {
        assertThatThrownBy(() -> service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Intervalo não pode ser nulo");
    }

    @Test
    void returnsEmptyForSundayWithoutQueryingStaff() {
        var result = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_SUNDAY, RangeType.DAY);

        assertThat(result).isEmpty();
        verify(customerManagementPortOut, never()).getScheduledStaff(any(), any());
    }

    @Test
    void returnsEmptyForSaturdayWhenServiceIsVeterinaryConsultation() {
        var result = service.findAvailableSlots(ServiceType.CONSULTA_VETERINARIA, FAR_FUTURE_SATURDAY, RangeType.DAY);

        assertThat(result).isEmpty();
        verify(customerManagementPortOut, never()).getScheduledStaff(any(), any());
    }

    @Test
    void returnsEmptyWhenNoStaffScheduledForTheDay() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"))).thenReturn(List.of());

        var result = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);

        assertThat(result).isEmpty();
        verify(bookingPortOut, never()).findActiveBookingsAt(any());
    }

    @Test
    void returnsAllFourteenWeekdaySlotsWhenStaffAvailableAndNoBookings() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER")))
                .thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(any())).thenReturn(List.of());

        var result = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);

        // Manhã: 09:00-11:30 (6) + Tarde: 13:00-16:30 (8) = 14 slots de 30min.
        assertThat(result).hasSize(14);
        assertThat(result).allSatisfy(slot -> assertThat(slot.availableStaff()).isEqualTo(1));
    }

    @Test
    void returnsAllSixSaturdaySlotsForGroomerService() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER")))
                .thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(any())).thenReturn(List.of());

        var result = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_SATURDAY, RangeType.DAY);

        // 08:00-10:30 de 30min = 6 slots.
        assertThat(result).hasSize(6);
    }

    @Test
    void excludesSlotWhereEntireRosterIsAlreadyAssigned() {
        var fullyBookedSlot = FAR_FUTURE_MONDAY.atTime(9, 0);
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER")))
                .thenReturn(List.of(staff("Ciclana")));
        lenient().when(bookingPortOut.findActiveBookingsAt(any())).thenReturn(List.of());

        var occupiedBooking = new Booking();
        occupiedBooking.setEmployeeName("Ciclana");
        when(bookingPortOut.findActiveBookingsAt(fullyBookedSlot)).thenReturn(List.of(occupiedBooking));

        var result = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);

        assertThat(result).noneMatch(slot -> slot.dateTime().equals(fullyBookedSlot));
        assertThat(result).hasSize(13);
    }

    @Test
    void weekRangeQueriesStaffForSixDaysExcludingSundayForGroomerService() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"))).thenReturn(List.of());

        service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.WEEK);

        verify(customerManagementPortOut, times(6)).getScheduledStaff(any(), eq("GROOMER"));
    }

    @Test
    void weekRangeQueriesStaffForFiveDaysExcludingSundayAndSaturdayForVeterinaryService() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("VETERINARIAN"))).thenReturn(List.of());

        service.findAvailableSlots(ServiceType.CONSULTA_VETERINARIA, FAR_FUTURE_MONDAY, RangeType.WEEK);

        verify(customerManagementPortOut, times(5)).getScheduledStaff(any(), eq("VETERINARIAN"));
    }

    @Test
    void monthRangeCoversEveryDayOfTheMonthExceptSundays() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"))).thenReturn(List.of());
        var referenceDate = FAR_FUTURE_MONDAY.withDayOfMonth(1);
        var yearMonth = java.time.YearMonth.from(referenceDate);
        long sundays = referenceDate.datesUntil(yearMonth.atEndOfMonth().plusDays(1))
                .filter(d -> d.getDayOfWeek() == DayOfWeek.SUNDAY)
                .count();
        long expectedCalls = yearMonth.lengthOfMonth() - sundays;

        service.findAvailableSlots(ServiceType.BANHO, referenceDate, RangeType.MONTH);

        verify(customerManagementPortOut, times((int) expectedCalls)).getScheduledStaff(any(), eq("GROOMER"));
    }
}
