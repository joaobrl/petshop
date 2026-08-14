package com.petshop.staff_service.core.application.service;

import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.staff_service.core.domain.Staff;
import com.petshop.staff_service.core.domain.WeekendAllocation;
import com.petshop.commons.security.Role;
import com.petshop.staff_service.core.port.in.WeekendAllocationPortIn;
import com.petshop.staff_service.core.port.out.StaffPortOut;
import com.petshop.staff_service.core.port.out.WeekendAllocationPortOut;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeekendAllocationService implements WeekendAllocationPortIn {

    @Value("${staff.groomer.saturday-staff-count:2}")
    private int saturdayStaffCount;

    private final StaffPortOut staffPortOut;
    private final WeekendAllocationPortOut weekendAllocationPortOut;

    @Override
    public List<WeekendAllocation> generateNextMonth() {
        var groomers = staffPortOut.findAll().stream()
                .filter(Staff::getEnabled)
                .filter(staff -> staff.getRole() == Role.GROOMER)
                .sorted(Comparator.comparing(Staff::getId))
                .toList();

        if (groomers.size() < saturdayStaffCount) {
            throw new BusinessRuleException(
                    "Não há funcionários de tosa/banho suficientes para preencher a escala de sábado (mínimo: "
                            + saturdayStaffCount + ", cadastrados e habilitados: " + groomers.size() + ")");
        }

        var targetMonth = nextMonthToFill();
        var saturdays = saturdaysOf(targetMonth);

        long cursor = weekendAllocationPortOut.countAll();
        List<WeekendAllocation> newAllocations = new ArrayList<>();

        for (LocalDate saturday : saturdays) {
            for (int i = 0; i < saturdayStaffCount; i++) {
                int index = (int) (cursor % groomers.size());
                newAllocations.add(new WeekendAllocation(saturday, groomers.get(index)));
                cursor++;
            }
        }

        var saved = weekendAllocationPortOut.saveAll(newAllocations);
        log.info("Escala de sábado gerada para {}: {} alocações criadas.", targetMonth, saved.size());
        return saved;
    }

    private YearMonth nextMonthToFill() {
        return weekendAllocationPortOut.findLastAllocatedDate()
                .map(lastDate -> YearMonth.from(lastDate).plusMonths(1))
                .orElse(YearMonth.now());
    }

    private List<LocalDate> saturdaysOf(YearMonth month) {
        List<LocalDate> saturdays = new ArrayList<>();
        var date = month.atDay(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SATURDAY));
        while (YearMonth.from(date).equals(month)) {
            saturdays.add(date);
            date = date.plusWeeks(1);
        }
        return saturdays;
    }
}
