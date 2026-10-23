package com.petshop.customermanagement.core.application.usecases;

import com.petshop.commons.exception.NotFoundException;
import com.petshop.customermanagement.core.domain.BookingHistory;
import com.petshop.customermanagement.core.port.in.dto.BookingCompletedCommand;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class UpdateBookingHistoryUseCase {

    private final BookingHistoryPortOut bookingHistoryPortOut;
    private final CustomerPortOut customerPortOut;

    public UpdateBookingHistoryUseCase(BookingHistoryPortOut bookingHistoryPortOut,
                                       CustomerPortOut customerPortOut) {
        this.bookingHistoryPortOut = bookingHistoryPortOut;
        this.customerPortOut = customerPortOut;
    }

    @Transactional(readOnly = true)
    public void execute(BookingCompletedCommand command) {
        var customer = customerPortOut.findByCpf(command.ownerCpf())
                .orElseThrow(() -> new NotFoundException("Cliente com CPF", command.ownerCpf()));

        BookingHistory history = bookingHistoryPortOut.findByBookingId(command.bookingId())
                .orElseGet(() -> {
                    BookingHistory newHistory = new BookingHistory();
                    newHistory.setBookingId(command.bookingId());
                    newHistory.setCustomerId(customer.getId());
                    return newHistory;
                });

        String date = command.bookingDateTime().toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String time = command.bookingDateTime().toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"));

        history.setServiceType(command.serviceType());
        history.setBookingDate(date);
        history.setBookingTime(time);
        history.setStatus(command.status());

        bookingHistoryPortOut.save(history);

        log.info("Histórico atualizado com sucesso para o cliente CPF: {}", command.ownerCpf());
    }
}