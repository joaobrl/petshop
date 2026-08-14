package com.petshop.booking_service.core.port.out;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingPortOut {
    Booking save(Booking booking);
    List<Booking> findByCriteria(BookingSearchCriteriaDto criteria);
    Optional<Booking> findById(UUID id);

    /**
     * Agendamentos (não cancelados) que já existem exatamente nesse horário.
     * Usado junto com a escala do staff-service pra decidir quais funcionários
     * já estão ocupados e escolher um livre pro novo agendamento.
     */
    List<Booking> findActiveBookingsAt(LocalDateTime dateTime);

    /**
     * Agendamentos (não cancelados) já existentes pro mesmo pet num dado
     * dia. Usado pra impedir que o mesmo pet seja agendado mais de uma vez
     * no mesmo dia.
     */
    List<Booking> findActiveBookingsForPetOnDate(UUID petId, LocalDate date);
}