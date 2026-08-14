package com.petshop.booking_service.core.application.service;

import com.petshop.booking_service.core.domain.AvailableSlot;
import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.port.in.AvailableSlotsPortIn;
import com.petshop.booking_service.core.port.out.BookingPortOut;
import com.petshop.booking_service.core.port.out.CustomerManagementPortOut;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


@Service
@RequiredArgsConstructor
public class AvailableSlotsService implements AvailableSlotsPortIn {

    private static final LocalTime WEEKDAY_START = LocalTime.of(9, 0);
    private static final LocalTime WEEKDAY_LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime WEEKDAY_LUNCH_END = LocalTime.of(13, 0);
    private static final LocalTime WEEKDAY_END = LocalTime.of(17, 0);

    private static final LocalTime SATURDAY_START = LocalTime.of(8, 0);
    private static final LocalTime SATURDAY_END = LocalTime.of(11, 0);

    private static final int SLOT_MINUTES = 30;
    private static final int MIN_HOURS_IN_ADVANCE = 2;

    private final BookingPortOut bookingPortOut;
    private final CustomerManagementPortOut customerManagementPortOut;

    @Override
    @Cacheable(cacheNames = "availableSlots")
    public List<AvailableSlot> findAvailableSlots(ServiceType serviceType, LocalDate referenceDate, RangeType range) {
        if (serviceType == null) throw new IllegalArgumentException("Service type cannot be null");
        if (referenceDate == null) throw new IllegalArgumentException("Reference date cannot be null");
        if (range == null) throw new IllegalArgumentException("Range cannot be null");

        var isVet = serviceType == ServiceType.CONSULTA_VETERINARIA;
        var role = serviceType.requiredStaffRole();
        var earliestBookable = LocalDateTime.now().plusHours(MIN_HOURS_IN_ADVANCE);

        List<AvailableSlot> result = new ArrayList<>();

        for (LocalDate day : datesOf(referenceDate, range)) {
            var dayOfWeek = day.getDayOfWeek();
            if (dayOfWeek == DayOfWeek.SUNDAY) continue;
            if (dayOfWeek == DayOfWeek.SATURDAY && isVet) continue;

            var representativeTime = dayOfWeek == DayOfWeek.SATURDAY ? SATURDAY_START : WEEKDAY_START;
            var roster = customerManagementPortOut.getScheduledStaff(day.atTime(representativeTime), role);
            if (roster.isEmpty()) continue;

            for (LocalTime slotTime : slotsOf(dayOfWeek)) {
                var slotDateTime = day.atTime(slotTime);
                if (!slotDateTime.isAfter(earliestBookable)) continue;

                Set<String> alreadyAssigned = bookingPortOut.findActiveBookingsAt(slotDateTime).stream()
                        .map(Booking::getEmployeeName)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

                long freeStaff = roster.stream()
                        .map(staffAssignment -> staffAssignment.name())
                        .filter(name -> !alreadyAssigned.contains(name))
                        .count();

                if (freeStaff > 0) {
                    result.add(new AvailableSlot(slotDateTime, (int) freeStaff));
                }
            }
        }

        return result;
    }

    private List<LocalDate> datesOf(LocalDate referenceDate, RangeType range) {
        return switch (range) {
            case DAY -> List.of(referenceDate);
            case WEEK -> {
                var monday = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield IntStream.range(0, 7)
                        .mapToObj(monday::plusDays)
                        .toList();
            }
            case MONTH -> {
                var yearMonth = YearMonth.from(referenceDate);
                yield IntStream.rangeClosed(1, yearMonth.lengthOfMonth())
                        .mapToObj(yearMonth::atDay)
                        .toList();
            }
        };
    }

    private List<LocalTime> slotsOf(DayOfWeek dayOfWeek) {
        List<LocalTime> slots = new ArrayList<>();

        if (dayOfWeek == DayOfWeek.SATURDAY) {
            var time = SATURDAY_START;
            while (time.isBefore(SATURDAY_END)) {
                slots.add(time);
                time = time.plusMinutes(SLOT_MINUTES);
            }
            return slots;
        }

        var morning = WEEKDAY_START;
        while (morning.isBefore(WEEKDAY_LUNCH_START)) {
            slots.add(morning);
            morning = morning.plusMinutes(SLOT_MINUTES);
        }

        var afternoon = WEEKDAY_LUNCH_END;
        while (afternoon.isBefore(WEEKDAY_END)) {
            slots.add(afternoon);
            afternoon = afternoon.plusMinutes(SLOT_MINUTES);
        }

        return slots;
    }
}
