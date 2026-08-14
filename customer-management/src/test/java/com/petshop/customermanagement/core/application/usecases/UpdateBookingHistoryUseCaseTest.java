package com.petshop.customermanagement.core.application.usecases;

import com.petshop.commons.exception.NotFoundException;
import com.petshop.customermanagement.core.domain.BookingHistory;
import com.petshop.customermanagement.core.domain.Customer;
import com.petshop.customermanagement.core.port.in.dto.BookingCompletedCommand;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.core.port.out.CustomerPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UpdateBookingHistoryUseCaseTest {

    @Mock
    private BookingHistoryPortOut bookingHistoryPortOut;

    @Mock
    private CustomerPortOut customerPortOut;

    private UpdateBookingHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateBookingHistoryUseCase(bookingHistoryPortOut, customerPortOut);
    }

    private BookingCompletedCommand command(UUID bookingId) {
        return new BookingCompletedCommand(
                bookingId,
                "12345678900",
                "Banho e Tosa",
                LocalDateTime.of(2026, 8, 10, 14, 30),
                "CONFIRMED"
        );
    }

    @Test
    void createsNewHistoryWhenNoneExistsForBooking() {
        var command = command(UUID.randomUUID());
        var customer = new Customer("Maria", "12345678900", "maria@mail.com", "119999999");
        when(customerPortOut.findByCpf("12345678900")).thenReturn(Optional.of(customer));
        when(bookingHistoryPortOut.findByBookingId(command.bookingId())).thenReturn(Optional.empty());
        when(bookingHistoryPortOut.save(any(BookingHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(command);

        ArgumentCaptor<BookingHistory> captor = ArgumentCaptor.forClass(BookingHistory.class);
        verify(bookingHistoryPortOut).save(captor.capture());
        var saved = captor.getValue();

        assertThat(saved.getBookingId()).isEqualTo(command.bookingId());
        assertThat(saved.getCustomerId()).isEqualTo(customer.getId());
        assertThat(saved.getServiceType()).isEqualTo("Banho e Tosa");
        assertThat(saved.getBookingDate()).isEqualTo("10/08/2026");
        assertThat(saved.getBookingTime()).isEqualTo("14:30");
        assertThat(saved.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void updatesExistingHistoryWhenAlreadyPresentForBooking() {
        var command = command(UUID.randomUUID());
        var customer = new Customer("Maria", "12345678900", "maria@mail.com", "119999999");
        var existing = new BookingHistory();
        existing.setBookingId(command.bookingId());
        existing.setCustomerId(customer.getId());
        existing.setStatus("PENDING");

        when(customerPortOut.findByCpf("12345678900")).thenReturn(Optional.of(customer));
        when(bookingHistoryPortOut.findByBookingId(command.bookingId())).thenReturn(Optional.of(existing));
        when(bookingHistoryPortOut.save(any(BookingHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(command);

        ArgumentCaptor<BookingHistory> captor = ArgumentCaptor.forClass(BookingHistory.class);
        verify(bookingHistoryPortOut).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void throwsNotFoundWhenCustomerCpfDoesNotExist() {
        var command = command(UUID.randomUUID());
        when(customerPortOut.findByCpf("12345678900")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(command))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("12345678900");

        verifyNoInteractions(bookingHistoryPortOut);
    }
}
