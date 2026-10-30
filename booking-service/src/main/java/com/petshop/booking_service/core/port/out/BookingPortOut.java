package com.petshop.booking_service.core.port.out;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingPortOut {
    Booking save(Booking booking);
    Page<Booking> findByCriteria(BookingSearchCriteriaDto criteria, Pageable pageable);
    Optional<Booking> findById(UUID id);

    /**
     * Agendamentos (não cancelados) que já existem exatamente nesse horário.
     * Usado junto com a escala do staff-service pra decidir quais funcionários
     * já estão ocupados e escolher um livre pro novo agendamento.
     */
    List<Booking> findActiveBookingsAt(LocalDateTime dateTime);

    /**
     * Trava o horário até o fim da transação corrente (commit/rollback), pra
     * que duas requisições concorrentes pro mesmo horário não escolham o
     * mesmo funcionário livre. Horários diferentes não se bloqueiam. Tem que
     * ser chamado dentro de uma transação, antes de findActiveBookingsAt.
     */
    void lockSlot(LocalDateTime dateTime);

    /**
     * Agendamentos (não cancelados) já existentes pro mesmo pet num dado
     * dia. Usado pra impedir que o mesmo pet seja agendado mais de uma vez
     * no mesmo dia.
     */
    List<Booking> findActiveBookingsForPetOnDate(UUID petId, LocalDate date);
}