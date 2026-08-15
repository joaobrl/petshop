package com.petshop.booking_service.core.domain;

import com.petshop.booking_service.core.domain.enums.PaymentMethod;
import com.petshop.booking_service.core.domain.enums.PaymentStatus;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.booking_service.core.port.in.dto.BookingUpdateDto;
import com.petshop.commons.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    private BookingRequestDto requestDto(ServiceType type, LocalDateTime dateTime, String observations) {
        var dto = new BookingRequestDto();
        dto.setPetId(UUID.randomUUID().toString());
        dto.setOwnerName("Ciclana");
        dto.setOwnerCpf("12345678900");
        dto.setOwnerContact("11999990000");
        dto.setOwnerEmail("ciclana@petshop.com");
        dto.setServiceType(type);
        dto.setBookingDateTime(dateTime);
        dto.setObservations(observations);
        return dto;
    }

    @Test
    void constructorFromRequestDtoInitializesAsScheduled() {
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime, "Sem observações");

        var booking = new Booking(dto);

        assertThat(booking.getServiceDetails().getServiceType()).isEqualTo(ServiceType.BANHO);
        assertThat(booking.getBookingDateTime()).isEqualTo(dateTime);
        assertThat(booking.getObservations()).isEqualTo("Sem observações");
        assertThat(booking.getStatus()).isEqualTo(StatusBooking.SCHEDULED);
    }

    @Test
    void constructorFromRequestDtoCopiesPetAndOwnerIdentity() {
        // Regressão: petId nulo quebraria silenciosamente a validação de "um agendamento por pet por dia".
        var dateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime, null);

        var booking = new Booking(dto);

        assertThat(booking.getPetId()).isEqualTo(UUID.fromString(dto.getPetId()));
        assertThat(booking.getOwnerName()).isEqualTo("Ciclana");
        assertThat(booking.getOwnerCpf()).isEqualTo("12345678900");
        assertThat(booking.getOwnerContact()).isEqualTo("11999990000");
        assertThat(booking.getOwnerEmail()).isEqualTo("ciclana@petshop.com");
    }

    @Test
    void completeBookingTransitionsFromScheduledToCompleted() {
        var booking = new Booking(requestDto(ServiceType.BANHO, LocalDateTime.of(2026, 8, 15, 9, 0), null));
        var update = new BookingUpdateDto();
        update.setObservations("Finalizado com sucesso");

        booking.completeBooking(update);

        assertThat(booking.getStatus()).isEqualTo(StatusBooking.COMPLETED);
        assertThat(booking.getObservations()).isEqualTo("Finalizado com sucesso");
    }

    @Test
    void completeBookingPreservesExistingObservationsWhenNotProvided() {
        // Regressão: um BookingUpdateDto sem observations (comum ao só confirmar a conclusão) não pode apagar
        // a observação já registrada durante o atendimento.
        var booking = new Booking(requestDto(ServiceType.BANHO, LocalDateTime.of(2026, 8, 15, 9, 0), "Pet agitado no banho"));

        booking.completeBooking(new BookingUpdateDto());

        assertThat(booking.getStatus()).isEqualTo(StatusBooking.COMPLETED);
        assertThat(booking.getObservations()).isEqualTo("Pet agitado no banho");
    }

    @Test
    void completeBookingRejectsNonScheduledBooking() {
        var booking = new Booking(requestDto(ServiceType.BANHO, LocalDateTime.of(2026, 8, 15, 9, 0), null));
        booking.setStatus(StatusBooking.CANCELED);

        assertThatThrownBy(() -> booking.completeBooking(new BookingUpdateDto()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Somente agendamentos com status agendado podem ser finalizados.");
    }

    @Test
    void updateAppliesOnlyNonNullFields() {
        var originalDateTime = LocalDateTime.of(2026, 8, 15, 9, 0);
        var booking = new Booking(requestDto(ServiceType.BANHO, originalDateTime, "original"));

        var update = new BookingUpdateDto();
        update.setObservations("atualizado");
        booking.update(update);

        assertThat(booking.getObservations()).isEqualTo("atualizado");
        assertThat(booking.getBookingDateTime()).isEqualTo(originalDateTime);
        assertThat(booking.getServiceDetails().getServiceType()).isEqualTo(ServiceType.BANHO);
    }

    @Test
    void updateReplacesServiceTypeAndDateTimeWhenProvided() {
        var booking = new Booking(requestDto(ServiceType.BANHO, LocalDateTime.of(2026, 8, 15, 9, 0), "original"));

        var update = new BookingUpdateDto();
        update.setServiceType(ServiceType.TOSAGEM);
        var newDateTime = LocalDateTime.of(2026, 8, 16, 10, 0);
        update.setBookingDateTime(newDateTime);
        booking.update(update);

        assertThat(booking.getServiceDetails().getServiceType()).isEqualTo(ServiceType.TOSAGEM);
        assertThat(booking.getBookingDateTime()).isEqualTo(newDateTime);
        assertThat(booking.getObservations()).isEqualTo("original");
    }

    @Test
    void noArgsAndAllArgsConstructorsAndSetters() {
        var booking = new Booking();
        booking.setOwnerName("Ciclana");
        assertThat(booking.getOwnerName()).isEqualTo("Ciclana");
    }

    @Test
    void updatePreservesPaymentConfirmationWhenServiceTypeChanges() {
        var booking = new Booking(requestDto(ServiceType.BANHO, LocalDateTime.of(2026, 8, 15, 9, 0), null));
        booking.confirmPayment(PaymentMethod.PIX);

        var update = new BookingUpdateDto();
        update.setServiceType(ServiceType.TOSAGEM);
        booking.update(update);

        assertThat(booking.getServiceDetails().getServiceType()).isEqualTo(ServiceType.TOSAGEM);
        assertThat(booking.getServiceDetails().getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(booking.getServiceDetails().getPaymentMethod()).isEqualTo(PaymentMethod.PIX);
    }

    @Test
    void confirmPaymentDelegatesToServiceDetails() {
        var booking = new Booking(requestDto(ServiceType.BANHO, LocalDateTime.of(2026, 8, 15, 9, 0), null));

        booking.confirmPayment(PaymentMethod.CARTAO);

        assertThat(booking.getServiceDetails().getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(booking.getServiceDetails().getPaymentMethod()).isEqualTo(PaymentMethod.CARTAO);
    }

    @Test
    void confirmPaymentRejectsCanceledBooking() {
        var booking = new Booking(requestDto(ServiceType.BANHO, LocalDateTime.of(2026, 8, 15, 9, 0), null));
        booking.setStatus(StatusBooking.CANCELED);

        assertThatThrownBy(() -> booking.confirmPayment(PaymentMethod.PIX))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Não é possível confirmar pagamento de um agendamento cancelado");
    }
}
