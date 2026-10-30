package com.petshop.booking_service.core.port.in;

import com.petshop.booking_service.core.domain.AvailableSlot;
import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;

import java.time.LocalDate;
import java.util.List;

public interface AvailableSlotsPortIn {

    List<AvailableSlot> findAvailableSlots(ServiceType serviceType, LocalDate referenceDate, RangeType range);

    void evictAvailableSlots(ServiceType serviceType, LocalDate date);
}
