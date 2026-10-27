package com.petshop.booking_service.core.application.service;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.StaffAssignment;
import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.port.out.BookingPortOut;
import com.petshop.booking_service.core.port.out.CustomerManagementPortOut;
import com.petshop.booking_service.infrastructure.config.CacheConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
    private static final LocalDate FAR_FUTURE_TUESDAY = FAR_FUTURE_MONDAY.plusDays(1);
    private static final LocalDate FAR_FUTURE_SATURDAY =
            FAR_FUTURE_MONDAY.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
    private static final LocalDate FAR_FUTURE_SUNDAY =
            FAR_FUTURE_MONDAY.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));

    @Mock
    private BookingPortOut bookingPortOut;
    @Mock
    private CustomerManagementPortOut customerManagementPortOut;

    private CacheManager cacheManager;
    private AvailableSlotsService service;

    @BeforeEach
    void setUp() {
        cacheManager = new ConcurrentMapCacheManager(CacheConfig.AVAILABLE_SLOTS_CACHE);
        service = new AvailableSlotsService(
                bookingPortOut, customerManagementPortOut, cacheManager, Clock.systemDefaultZone());
    }

    private AvailableSlotsService serviceWithFixedClock(LocalDateTime fixedNow) {
        var zone = ZoneId.systemDefault();
        var clock = Clock.fixed(fixedNow.atZone(zone).toInstant(), zone);
        return new AvailableSlotsService(bookingPortOut, customerManagementPortOut, cacheManager, clock);
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
        verify(customerManagementPortOut, never()).getScheduledStaff(any(), any(), any());
    }

    @Test
    void returnsEmptyForSaturdayWhenServiceIsVeterinaryConsultation() {
        var result = service.findAvailableSlots(ServiceType.CONSULTA_VETERINARIA, FAR_FUTURE_SATURDAY, RangeType.DAY);

        assertThat(result).isEmpty();
        verify(customerManagementPortOut, never()).getScheduledStaff(any(), any(), any());
    }

    @Test
    void returnsEmptyWhenNoStaffScheduledForTheDay() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any())).thenReturn(List.of());

        var result = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);

        assertThat(result).isEmpty();
        verify(bookingPortOut, never()).findActiveBookingsAt(any());
    }

    @Test
    void returnsAllFourteenWeekdaySlotsWhenStaffAvailableAndNoBookings() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any()))
                .thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(any())).thenReturn(List.of());

        var result = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);

        // Manhã: 09:00-11:30 (6) + Tarde: 13:00-16:30 (8) = 14 slots de 30min.
        assertThat(result).hasSize(14);
        assertThat(result).allSatisfy(slot -> assertThat(slot.availableStaff()).isEqualTo(1));
    }

    @Test
    void returnsAllSixSaturdaySlotsForGroomerService() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any()))
                .thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(any())).thenReturn(List.of());

        var result = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_SATURDAY, RangeType.DAY);

        // 08:00-10:30 de 30min = 6 slots.
        assertThat(result).hasSize(6);
    }

    @Test
    void excludesSlotWhereEntireRosterIsAlreadyAssignedAndIgnoresNullEmployeeNames() {
        var fullyBookedSlot = FAR_FUTURE_MONDAY.atTime(9, 0);
        var slotWithUnassignedBooking = FAR_FUTURE_MONDAY.atTime(9, 30);
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any()))
                .thenReturn(List.of(staff("Ciclana")));
        lenient().when(bookingPortOut.findActiveBookingsAt(any())).thenReturn(List.of());

        var occupiedBooking = new Booking();
        occupiedBooking.setEmployeeName("Ciclana");
        when(bookingPortOut.findActiveBookingsAt(fullyBookedSlot)).thenReturn(List.of(occupiedBooking));

        var unassignedBooking = new Booking();
        unassignedBooking.setEmployeeName(null);
        when(bookingPortOut.findActiveBookingsAt(slotWithUnassignedBooking)).thenReturn(List.of(unassignedBooking));

        var result = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);

        assertThat(result).noneMatch(slot -> slot.dateTime().equals(fullyBookedSlot));
        assertThat(result).anyMatch(slot -> slot.dateTime().equals(slotWithUnassignedBooking));
        assertThat(result).hasSize(13);
    }

    @Test
    void weekRangeQueriesStaffForSixDaysExcludingSundayForGroomerService() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any())).thenReturn(List.of());

        service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.WEEK);

        verify(customerManagementPortOut, times(6)).getScheduledStaff(any(), eq("GROOMER"), any());
    }

    @Test
    void weekRangeQueriesStaffForFiveDaysExcludingSundayAndSaturdayForVeterinaryService() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("VETERINARIAN"), any())).thenReturn(List.of());

        service.findAvailableSlots(ServiceType.CONSULTA_VETERINARIA, FAR_FUTURE_MONDAY, RangeType.WEEK);

        verify(customerManagementPortOut, times(5)).getScheduledStaff(any(), eq("VETERINARIAN"), any());
    }

    @Test
    void monthRangeCoversEveryDayOfTheMonthExceptSundays() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any())).thenReturn(List.of());
        var referenceDate = FAR_FUTURE_MONDAY.withDayOfMonth(1);
        var yearMonth = java.time.YearMonth.from(referenceDate);
        long sundays = referenceDate.datesUntil(yearMonth.atEndOfMonth().plusDays(1))
                .filter(d -> d.getDayOfWeek() == DayOfWeek.SUNDAY)
                .count();
        long expectedCalls = yearMonth.lengthOfMonth() - sundays;

        service.findAvailableSlots(ServiceType.BANHO, referenceDate, RangeType.MONTH);

        verify(customerManagementPortOut, times((int) expectedCalls)).getScheduledStaff(any(), eq("GROOMER"), any());
    }

    // ---------- cache por (cargo, dia) e compartilhamento entre serviços/intervalos ----------

    @Test
    void cachesDailySlotsAndAvoidsRepeatedQueriesOnSubsequentCallForSameDayAndRole() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any()))
                .thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(any())).thenReturn(List.of());

        var first = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);
        var second = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);

        assertThat(second).isEqualTo(first);
        verify(customerManagementPortOut, times(1)).getScheduledStaff(any(), eq("GROOMER"), any());
        verify(bookingPortOut, times(14)).findActiveBookingsAt(any());
    }

    @Test
    void sharesCachedDailySlotsAcrossAllGroomerServicesOnSameDate() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any()))
                .thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(any())).thenReturn(List.of());

        var banhoSlots = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);
        var tosagemSlots = service.findAvailableSlots(ServiceType.TOSAGEM, FAR_FUTURE_MONDAY, RangeType.DAY);
        var tosaEBanhoSlots = service.findAvailableSlots(ServiceType.TOSA_E_BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);

        assertThat(tosagemSlots).isEqualTo(banhoSlots);
        assertThat(tosaEBanhoSlots).isEqualTo(banhoSlots);
        verify(customerManagementPortOut, times(1)).getScheduledStaff(any(), eq("GROOMER"), any());
    }

    @Test
    void keepsSeparateCacheEntriesForGroomerAndVeterinarianOnSameDate() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any()))
                .thenReturn(List.of(staff("Ciclana")));
        when(customerManagementPortOut.getScheduledStaff(any(), eq("VETERINARIAN"), any()))
                .thenReturn(List.of(staff("Dr. Fulano"), staff("Dra. Beltrana")));
        when(bookingPortOut.findActiveBookingsAt(any())).thenReturn(List.of());

        var groomerSlots = service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);
        var vetSlots = service.findAvailableSlots(ServiceType.CONSULTA_VETERINARIA, FAR_FUTURE_MONDAY, RangeType.DAY);

        assertThat(groomerSlots).allSatisfy(slot -> assertThat(slot.availableStaff()).isEqualTo(1));
        assertThat(vetSlots).allSatisfy(slot -> assertThat(slot.availableStaff()).isEqualTo(2));
        verify(customerManagementPortOut, times(1)).getScheduledStaff(any(), eq("GROOMER"), any());
        verify(customerManagementPortOut, times(1)).getScheduledStaff(any(), eq("VETERINARIAN"), any());
    }

    @Test
    void reusesCachedDayWhenQueryingWeekRangeAfterDayRange() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any())).thenReturn(List.of());

        // Popula segunda-feira via consulta DAY (1 chamada ao staff-service).
        service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);
        // Consulta WEEK na mesma semana deve reaproveitar segunda-feira do cache e consultar apenas terça..sábado (+5).
        service.findAvailableSlots(ServiceType.TOSAGEM, FAR_FUTURE_MONDAY, RangeType.WEEK);

        verify(customerManagementPortOut, times(6)).getScheduledStaff(any(), eq("GROOMER"), any());
    }

    @Test
    void filtersOutSlotsWithinMinimumAdvanceWindowDynamicallyEvenWhenServedFromCache() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any()))
                .thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(any())).thenReturn(List.of());

        // Às 08:00 (earliestBookable = 10:00), 09:00, 09:30 e 10:00 ficam de fora -> sobram 11 slots (10:30..16:30).
        var atEightAm = serviceWithFixedClock(FAR_FUTURE_MONDAY.atTime(8, 0));
        var morningResult = atEightAm.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);
        assertThat(morningResult).hasSize(11);
        assertThat(morningResult.get(0).dateTime()).isEqualTo(FAR_FUTURE_MONDAY.atTime(10, 30));

        // Às 09:30 (earliestBookable = 11:30), lê a MESMA entrada do cache (sem nova chamada ao staff-service),
        // mas filtra 10:30, 11:00 e 11:30 -> sobram só os 8 slots da tarde (13:00..16:30).
        var atNineThirtyAm = serviceWithFixedClock(FAR_FUTURE_MONDAY.atTime(9, 30));
        var laterResult = atNineThirtyAm.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);
        assertThat(laterResult).hasSize(8);
        assertThat(laterResult.get(0).dateTime()).isEqualTo(FAR_FUTURE_MONDAY.atTime(13, 0));

        verify(customerManagementPortOut, times(1)).getScheduledStaff(any(), eq("GROOMER"), any());
    }

    @Test
    void evictAvailableSlotsRemovesOnlyTargetRoleAndDateFromCache() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any())).thenReturn(List.of());
        when(customerManagementPortOut.getScheduledStaff(any(), eq("VETERINARIAN"), any())).thenReturn(List.of());

        service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);
        service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_TUESDAY, RangeType.DAY);
        service.findAvailableSlots(ServiceType.CONSULTA_VETERINARIA, FAR_FUTURE_MONDAY, RangeType.DAY);

        // Invalida GROOMER na segunda-feira (via TOSAGEM, que mapeia pro mesmo cargo GROOMER).
        service.evictAvailableSlots(ServiceType.TOSAGEM, FAR_FUTURE_MONDAY);

        // Segunda-feira GROOMER sofre cache miss (+1 chamada); terça GROOMER e segunda VETERINARIAN continuam em cache.
        service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);
        service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_TUESDAY, RangeType.DAY);
        service.findAvailableSlots(ServiceType.CONSULTA_VETERINARIA, FAR_FUTURE_MONDAY, RangeType.DAY);

        verify(customerManagementPortOut, times(2))
                .getScheduledStaff(FAR_FUTURE_MONDAY.atTime(9, 0), "GROOMER", null);
        verify(customerManagementPortOut, times(1))
                .getScheduledStaff(FAR_FUTURE_TUESDAY.atTime(9, 0), "GROOMER", null);
        verify(customerManagementPortOut, times(1))
                .getScheduledStaff(FAR_FUTURE_MONDAY.atTime(9, 0), "VETERINARIAN", null);
    }

    @Test
    void evictAvailableSlotsSafelyIgnoresNullServiceTypeOrNullDate() {
        when(customerManagementPortOut.getScheduledStaff(any(), eq("GROOMER"), any())).thenReturn(List.of());

        service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);

        service.evictAvailableSlots(null, FAR_FUTURE_MONDAY);
        service.evictAvailableSlots(ServiceType.BANHO, null);

        service.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY);
        verify(customerManagementPortOut, times(1)).getScheduledStaff(any(), eq("GROOMER"), any());
    }
}
