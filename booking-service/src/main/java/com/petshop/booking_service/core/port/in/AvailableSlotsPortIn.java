package com.petshop.booking_service.core.port.in;

import com.petshop.booking_service.core.domain.AvailableSlot;
import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;

import java.time.LocalDate;
import java.util.List;

public interface AvailableSlotsPortIn {

    /**
     * Lista os horários realmente disponíveis pra agendar {@code serviceType}
     * dentro da janela definida por {@code range} a partir de
     * {@code referenceDate} (o próprio dia, a semana ou o mês que o contém).
     */
    List<AvailableSlot> findAvailableSlots(ServiceType serviceType, LocalDate referenceDate, RangeType range);
}
