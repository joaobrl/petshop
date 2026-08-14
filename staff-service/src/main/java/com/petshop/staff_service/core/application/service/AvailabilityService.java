package com.petshop.staff_service.core.application.service;

import com.petshop.staff_service.core.domain.AvailabilityResult;
import com.petshop.staff_service.core.domain.Staff;
import com.petshop.commons.security.Role;
import com.petshop.staff_service.core.port.in.AvailabilityPortIn;
import com.petshop.staff_service.core.port.out.StaffPortOut;
import com.petshop.staff_service.core.port.out.WeekendAllocationPortOut;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Calcula quantos funcionários estão escalados pra atender num determinado
 * dia/horário e cargo. RECEPTIONIST não entra nessas contas — não é um
 * cargo "atendível" via booking-service. No sábado só há escala pra GROOMER,
 * vinda da Alocação Final de Semana gerada por {@code WeekendAllocationService}
 * — se o mês ainda não foi gerado, ninguém está disponível. Este serviço não
 * sabe quantos agendamentos já existem no horário; isso é responsabilidade
 * do booking-service.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService implements AvailabilityPortIn {

    private static final LocalTime WEEKDAY_START = LocalTime.of(9, 0);
    private static final LocalTime WEEKDAY_LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime WEEKDAY_LUNCH_END = LocalTime.of(13, 0);
    private static final LocalTime WEEKDAY_END = LocalTime.of(17, 0);

    private static final LocalTime SATURDAY_START = LocalTime.of(8, 0);
    private static final LocalTime SATURDAY_END = LocalTime.of(11, 0);

    private static final int SLOT_MINUTES = 30;

    private final StaffPortOut staffPortOut;
    private final WeekendAllocationPortOut weekendAllocationPortOut;

    @Override
    public AvailabilityResult checkGeneralAvailability(LocalDateTime dateTime, Role role) {
        if (dateTime == null) {
            throw new IllegalArgumentException("dateTime é obrigatório");
        }
        if (role == null) {
            throw new IllegalArgumentException("role é obrigatório");
        }

        if (!isValidSlot(dateTime, role)) {
            return new AvailabilityResult(0, false, dateTime);
        }

        int capacity = scheduledStaffFor(dateTime, role).size();
        return new AvailabilityResult(capacity, capacity > 0, dateTime);
    }

    @Override
    public List<Staff> scheduledStaffFor(LocalDateTime dateTime, Role role) {
        if (dateTime == null || role == null || !isValidSlot(dateTime, role)) {
            return List.of();
        }

        var enabledStaffOfRole = staffPortOut.findAll().stream()
                .filter(Staff::getEnabled)
                .filter(staff -> staff.getRole() == role)
                .toList();

        if (enabledStaffOfRole.isEmpty()) {
            return List.of();
        }

        if (dateTime.getDayOfWeek() == DayOfWeek.SATURDAY) {
            // isValidSlot já garante que só GROOMER chega aqui num sábado.
            return staffOnDutySaturday(dateTime.toLocalDate(), enabledStaffOfRole);
        }

        return enabledStaffOfRole;
    }

    private List<Staff> staffOnDutySaturday(LocalDate saturday, List<Staff> enabledGroomers) {
        Map<java.util.UUID, Staff> staffById = enabledGroomers.stream()
                .collect(Collectors.toMap(Staff::getId, staff -> staff));

        return weekendAllocationPortOut.findByDate(saturday).stream()
                .map(allocation -> staffById.get(allocation.getStaffId()))
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isValidSlot(LocalDateTime dateTime, Role role) {
        if (dateTime.getMinute() % SLOT_MINUTES != 0 || dateTime.getSecond() != 0) {
            return false;
        }

        DayOfWeek day = dateTime.getDayOfWeek();
        LocalTime time = dateTime.toLocalTime();

        if (day == DayOfWeek.SUNDAY) {
            return false;
        }

        if (day == DayOfWeek.SATURDAY) {
            if (role != Role.GROOMER) {
                return false;
            }
            return !time.isBefore(SATURDAY_START) && time.isBefore(SATURDAY_END);
        }

        boolean withinMorning = !time.isBefore(WEEKDAY_START) && time.isBefore(WEEKDAY_LUNCH_START);
        boolean withinAfternoon = !time.isBefore(WEEKDAY_LUNCH_END) && time.isBefore(WEEKDAY_END);
        return withinMorning || withinAfternoon;
    }
}
