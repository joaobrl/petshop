package com.petshop.booking_service.infrastructure.persistence.postgresql.repository;

import com.petshop.booking_service.core.domain.ServiceDetails;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.infrastructure.persistence.postgresql.entity.BookingEntity;
import com.petshop.booking_service.infrastructure.persistence.postgresql.entity.ServiceDetailsEmbeddable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookingRepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void findsByOwnerCpfAndServiceTypeAndDate() {
        var bookingDateTime = LocalDateTime.of(2026, 8, 10, 9, 0);
        bookingRepository.save(newBookingEntity("11122233344", ServiceType.BANHO, bookingDateTime));
        bookingRepository.save(newBookingEntity("99988877766", ServiceType.TOSAGEM, bookingDateTime.plusDays(1)));

        var criteria = new BookingSearchCriteriaDto("11122233344", null, "BANHO", null, bookingDateTime.toLocalDate(), null);
        var results = bookingRepository.findByCriteria(criteria,
                criteria.getDate().atStartOfDay(),
                criteria.getDate().plusDays(1).atStartOfDay(),
                PageRequest.of(0, 20));

        assertThat(results.getContent()).hasSize(1);
        assertThat(results.getContent().get(0).getOwnerCpf()).isEqualTo("11122233344");
    }

    @Test
    void findsByCriteriaWithAllFieldsNullReturnsEverything() {
        bookingRepository.save(newBookingEntity("11122233344", ServiceType.BANHO, LocalDateTime.of(2026, 8, 10, 9, 0)));
        bookingRepository.save(newBookingEntity("99988877766", ServiceType.TOSAGEM, LocalDateTime.of(2026, 8, 11, 10, 0)));

        var criteria = new BookingSearchCriteriaDto(null, null, null, null, null, null);
        var results = bookingRepository.findByCriteria(criteria, null, null, PageRequest.of(0, 20));

        assertThat(results.getContent()).hasSize(2);
    }

    @Test
    void findsActiveBookingsAtExactDateTimeExcludingCanceled() {
        var dateTime = LocalDateTime.of(2026, 8, 10, 9, 0);

        var active = newBookingEntity("11122233344", ServiceType.BANHO, dateTime);
        active.setEmployeeName("Funcionario Um");
        bookingRepository.save(active);

        var canceled = newBookingEntity("99988877766", ServiceType.BANHO, dateTime);
        canceled.setStatus(StatusBooking.CANCELED);
        canceled.setEmployeeName("Funcionario Dois");
        bookingRepository.save(canceled);

        var results = bookingRepository.findByBookingDateTimeAndStatusNot(dateTime, StatusBooking.CANCELED);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getEmployeeName()).isEqualTo("Funcionario Um");
    }

    @Test
    void findsActiveBookingsForPetOnDateExcludingCanceledAndOtherDays() {
        var petId = UUID.randomUUID();
        var day = LocalDateTime.of(2026, 8, 10, 9, 0);

        var activeSameDay = newBookingEntity("11122233344", ServiceType.BANHO, day);
        activeSameDay.setPetId(petId);
        bookingRepository.save(activeSameDay);

        var canceledSameDay = newBookingEntity("11122233344", ServiceType.TOSAGEM, day.plusHours(1));
        canceledSameDay.setPetId(petId);
        canceledSameDay.setStatus(StatusBooking.CANCELED);
        bookingRepository.save(canceledSameDay);

        var activeOtherDay = newBookingEntity("11122233344", ServiceType.BANHO, day.plusDays(1));
        activeOtherDay.setPetId(petId);
        bookingRepository.save(activeOtherDay);

        var activeOtherPetSameDay = newBookingEntity("99988877766", ServiceType.BANHO, day.plusHours(2));
        bookingRepository.save(activeOtherPetSameDay);

        var results = bookingRepository.findByPetIdAndBookingDateTimeBetweenAndStatusNot(
                petId, day.toLocalDate().atStartOfDay(), day.toLocalDate().plusDays(1).atStartOfDay(), StatusBooking.CANCELED);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(activeSameDay.getId());
    }

    private BookingEntity newBookingEntity(String ownerCpf, ServiceType serviceType, LocalDateTime dateTime) {
        // ServiceDetails(ServiceType) carrega a lógica de preço/duração; o embeddable de persistência não tem
        // esse construtor de conveniência (regra de negócio não pertence à camada de infra), então copiamos os valores.
        var serviceDetails = new ServiceDetails(serviceType);
        var booking = new BookingEntity();
        booking.setPetId(UUID.randomUUID());
        booking.setOwnerName("Cliente Teste");
        booking.setOwnerCpf(ownerCpf);
        booking.setOwnerContact("11999990000");
        booking.setServiceDetails(new ServiceDetailsEmbeddable(
                serviceDetails.getServiceType(), serviceDetails.getPrice(), serviceDetails.getDurationInMinutes(),
                serviceDetails.getPaymentStatus(), serviceDetails.getPaymentMethod()));
        booking.setBookingDateTime(dateTime);
        booking.setStatus(StatusBooking.SCHEDULED);
        return booking;
    }
}
