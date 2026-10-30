package com.petshop.booking_service.core.application.service;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.StaffAssignment;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.domain.exception.BookingUnavailableException;
import com.petshop.booking_service.core.port.in.AvailableSlotsPortIn;
import com.petshop.booking_service.core.port.in.BookingPortIn;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.booking_service.core.port.out.BookingHistoryPortOut;
import com.petshop.booking_service.core.port.out.BookingPortOut;
import com.petshop.booking_service.core.port.out.CustomerManagementPortOut;
import com.petshop.booking_service.core.port.out.NotificationPortOut;
import com.petshop.booking_service.infrastructure.persistence.postgresql.repository.BookingRepository;
import com.petshop.commons.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * SDD-005: corrida real entre transações num Postgres de verdade
 * (pg_advisory_xact_lock não existe no H2). Só a escala do staff-service,
 * Kafka e o cálculo de horários livres são mockados — persistência e
 * transação são as reais. Testes de corrida rodam repetidos pra não passar
 * por sorte de escalonamento.
 */
@SpringBootTest
@Testcontainers
class BookingSlotLockIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");

    // Horário fixo numa segunda-feira futura dentro do expediente; requisições como ADMIN dispensam só a
    // antecedência mínima, então a data não depende de quando o teste roda.
    private static final LocalDateTime TEN_O_CLOCK = LocalDate.now().plusYears(1)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(10, 0);

    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired
    private BookingPortIn bookingPortIn;
    @Autowired
    private BookingPortOut bookingPortOut;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private CustomerManagementPortOut customerManagementPortOut;
    @MockitoBean
    private NotificationPortOut notificationPortOut;
    @MockitoBean
    private BookingHistoryPortOut bookingHistoryPortOut;
    @MockitoBean
    private AvailableSlotsPortIn availableSlotsPortIn;

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        when(availableSlotsPortIn.findAvailableSlots(any(), any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        bookingRepository.deleteAll();
    }

    // CA1 / RF1
    @RepeatedTest(20)
    void concurrentRequestsForSingleFreeGroomerYieldExactlyOneBooking() throws Exception {
        givenScheduledGroomers("Groomer A");

        var outcomes = createConcurrently(CONCURRENT_REQUESTS);

        assertThat(outcomes.successes()).hasSize(1);
        assertThat(outcomes.failures()).hasSize(CONCURRENT_REQUESTS - 1)
                .allSatisfy(failure -> assertThat(failure)
                        .isInstanceOf(BookingUnavailableException.class)
                        .hasMessageStartingWith("Não há funcionário disponível para o horário selecionado"));
        assertThat(bookingPortOut.findActiveBookingsAt(TEN_O_CLOCK)).hasSize(1);
    }

    // CA2 / RF2
    @RepeatedTest(20)
    void concurrentRequestsForTwoFreeGroomersGetDifferentEmployees() throws Exception {
        givenScheduledGroomers("Groomer A", "Groomer B");

        var outcomes = createConcurrently(2);

        assertThat(outcomes.failures()).isEmpty();
        assertThat(outcomes.successes()).extracting(Booking::getEmployeeName)
                .containsExactlyInAnyOrder("Groomer A", "Groomer B");
    }

    // CA3 / RF3
    @Test
    void canceledBookingDoesNotBlockEmployeeAtSameSlot() {
        givenScheduledGroomers("Groomer A");
        var canceled = bookingPortIn.createBooking(request(), Role.ADMIN, null);
        bookingPortIn.cancelBooking(canceled.getId());

        var rebooked = bookingPortIn.createBooking(request(), Role.ADMIN, null);

        assertThat(rebooked.getEmployeeName()).isEqualTo("Groomer A");
    }

    // CA4 / RNF1 — verificado direto no lock (determinístico) em vez de medir tempo de createBooking.
    @Test
    void slotLockBlocksSameSlotButNotDifferentSlots() throws Exception {
        var holdingLock = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            bookingPortOut.lockSlot(TEN_O_CLOCK);
            holdingLock.countDown();
            await(release);
        }));
        assertThat(holdingLock.await(10, TimeUnit.SECONDS)).isTrue();

        var otherSlot = executor.submit(() -> lockInNewTransaction(TEN_O_CLOCK.plusHours(1)));
        var sameSlot = executor.submit(() -> lockInNewTransaction(TEN_O_CLOCK));

        otherSlot.get(5, TimeUnit.SECONDS);
        assertThatThrownBy(() -> sameSlot.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

        release.countDown();
        holder.get(5, TimeUnit.SECONDS);
        sameSlot.get(5, TimeUnit.SECONDS);
    }

    private void lockInNewTransaction(LocalDateTime slot) {
        transactionTemplate.executeWithoutResult(status -> bookingPortOut.lockSlot(slot));
    }

    private void givenScheduledGroomers(String... names) {
        var staff = new ArrayList<StaffAssignment>();
        for (var name : names) {
            staff.add(new StaffAssignment(UUID.randomUUID(), name));
        }
        when(customerManagementPortOut.getScheduledStaff(any(LocalDateTime.class), eq("GROOMER"), any()))
                .thenReturn(staff);
    }

    private Outcomes createConcurrently(int requests) throws Exception {
        var ready = new CountDownLatch(requests);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<Booking>>();
        for (int i = 0; i < requests; i++) {
            var request = request();
            futures.add(executor.submit(() -> {
                ready.countDown();
                await(start);
                return bookingPortIn.createBooking(request, Role.ADMIN, null);
            }));
        }
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        var outcomes = new Outcomes(new ArrayList<>(), new ArrayList<>());
        for (var future : futures) {
            try {
                outcomes.successes().add(future.get(30, TimeUnit.SECONDS));
            } catch (ExecutionException e) {
                outcomes.failures().add(e.getCause());
            }
        }
        return outcomes;
    }

    // Pet diferente por requisição, senão a regra de "mesmo pet no mesmo dia" barra antes de chegar no lock.
    private BookingRequestDto request() {
        var dto = new BookingRequestDto();
        dto.setPetId(UUID.randomUUID().toString());
        dto.setOwnerName("Cliente Teste");
        dto.setOwnerCpf("12345678900");
        dto.setOwnerContact("11999990000");
        dto.setOwnerEmail("cliente@petshop.com");
        dto.setServiceType(ServiceType.BANHO);
        dto.setBookingDateTime(TEN_O_CLOCK);
        return dto;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timeout esperando latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record Outcomes(List<Booking> successes, List<Throwable> failures) {
    }
}
