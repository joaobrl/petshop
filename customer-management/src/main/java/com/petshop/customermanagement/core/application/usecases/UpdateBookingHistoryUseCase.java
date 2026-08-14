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

    // Chamado a partir do @KafkaListener, sem requisição HTTP em andamento —
    // sem @Transactional aqui, a sessão Hibernate que carrega o Customer em
    // customerPortOut.findByCpf() fecha assim que o método retorna, e o
    // CustomerMapper (que acessa a coleção lazy "pet" pra montar o domínio
    // completo) explode com LazyInitializationException. Em fluxos disparados
    // por HTTP isso nunca aparece por causa do spring.jpa.open-in-view
    // (default true), que mantém a sessão aberta até a resposta ser
    // serializada — mascarando esse mesmo problema em qualquer chamada fora
    // de um request web.
    @Transactional(readOnly = true)
    public void execute(BookingCompletedCommand command) {
        // O command não tem customerId — busca o cliente pelo CPF.
        var customer = customerPortOut.findByCpf(command.ownerCpf())
                .orElseThrow(() -> new NotFoundException("Customer with CPF", command.ownerCpf()));

        BookingHistory history = bookingHistoryPortOut.findByBookingId(command.bookingId())
                .orElseGet(() -> {
                    BookingHistory newHistory = new BookingHistory();
                    newHistory.setBookingId(command.bookingId());
                    newHistory.setCustomerId(customer.getId());
                    return newHistory;
                });

        // Mongo salva data e hora em Strings separadas; o evento manda um LocalDateTime único.
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