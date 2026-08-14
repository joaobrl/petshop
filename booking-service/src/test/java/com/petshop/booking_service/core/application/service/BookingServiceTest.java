package com.petshop.booking_service.core.application.service;

import com.petshop.booking_service.core.domain.AvailableSlot;
import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.StaffAssignment;
import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
import com.petshop.booking_service.core.domain.exception.BookingUnavailableException;
import com.petshop.booking_service.core.port.in.AvailableSlotsPortIn;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.core.port.in.dto.BookingUpdateDto;
import com.petshop.booking_service.core.port.out.BookingHistoryPortOut;
import com.petshop.booking_service.core.port.out.BookingPortOut;
import com.petshop.booking_service.core.port.out.CustomerManagementPortOut;
import com.petshop.booking_service.core.port.out.NotificationPortOut;
import com.petshop.commons.exception.BusinessRuleException;
import com.petshop.commons.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Datas âncora calculadas em relação a "daqui a 1 ano" (não fixas) pra
 * garantir sempre mais de 2h de antecedência da regra de
 * MIN_HOURS_IN_ADVANCE em BookingService, não importa quando o teste rodar.
 */
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final LocalDate FAR_FUTURE_MONDAY =
            LocalDate.now().plusYears(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private static final LocalDate FAR_FUTURE_SATURDAY =
            FAR_FUTURE_MONDAY.with(TemporalAdjusters.next(DayOfWeek.SATURDAY));
    private static final LocalDate FAR_FUTURE_SUNDAY =
            FAR_FUTURE_MONDAY.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));

    @Mock
    private BookingPortOut bookingPortOut;
    @Mock
    private CustomerManagementPortOut customerManagementPortOut;
    @Mock
    private NotificationPortOut notificationPortOut;
    @Mock
    private BookingHistoryPortOut bookingHistoryPortOut;
    @Mock
    private AvailableSlotsPortIn availableSlotsPortIn;

    private BookingService service;

    @BeforeEach
    void setUp() {
        // Padrão "sem sugestão", evita NPE em findNextAvailableSlotSameDay; lenient() porque nem todo teste
        // desta classe passa pelo caminho de "sem funcionário disponível" que consulta esse mock.
        lenient().when(availableSlotsPortIn.findAvailableSlots(any(), any(), any())).thenReturn(List.of());
        // Padrão "sem conflito", evita NPE em validateNoConflictingPetBooking — mesmo motivo acima.
        lenient().when(bookingPortOut.findActiveBookingsForPetOnDate(any(), any())).thenReturn(List.of());
        service = new BookingService(bookingPortOut, customerManagementPortOut, notificationPortOut, bookingHistoryPortOut, availableSlotsPortIn);
    }

    private BookingRequestDto requestDto(ServiceType serviceType, LocalDateTime dateTime) {
        return requestDto(serviceType, dateTime, UUID.randomUUID());
    }

    private BookingRequestDto requestDto(ServiceType serviceType, LocalDateTime dateTime, UUID petId) {
        var dto = new BookingRequestDto();
        dto.setPetId(petId.toString());
        dto.setOwnerName("Ciclana");
        dto.setOwnerCpf("12345678900");
        dto.setOwnerContact("11999990000");
        dto.setOwnerEmail("ciclana@petshop.com");
        dto.setServiceType(serviceType);
        dto.setBookingDateTime(dateTime);
        return dto;
    }

    private StaffAssignment staff(String name) {
        return new StaffAssignment(UUID.randomUUID(), name);
    }

    // ---------- validação de horário (via createBooking) ----------

    @Test
    void rejectsNullBookingDateTime() {
        var dto = requestDto(ServiceType.BANHO, null);

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Booking date and time cannot be null");
    }

    @Test
    void rejectsNullServiceType() {
        var dto = requestDto(null, FAR_FUTURE_MONDAY.atTime(9, 0));

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Service type cannot be null");
    }

    @Test
    void rejectsSundayBooking() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_SUNDAY.atTime(9, 0));

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The store is closed on Sundays");
    }

    @Test
    void rejectsVeterinaryConsultationOnSaturday() {
        var dto = requestDto(ServiceType.CONSULTA_VETERINARIA, FAR_FUTURE_SATURDAY.atTime(9, 0));

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Veterinary consultations are only available Monday through Friday");
    }

    @Test
    void rejectsWeekdayBookingBeforeOpeningHours() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(8, 0));

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bookings on weekdays must be between 9 AM and 5 PM, with a break from 12 PM to 1 PM");
    }

    @Test
    void rejectsWeekdayBookingDuringLunchBreak() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(12, 30));

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bookings on weekdays must be between 9 AM and 5 PM, with a break from 12 PM to 1 PM");
    }

    @Test
    void rejectsWeekdayBookingAfterClosingHours() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(17, 30));

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bookings on weekdays must be between 9 AM and 5 PM, with a break from 12 PM to 1 PM");
    }

    @Test
    void rejectsSaturdayBookingOutsideAllowedHours() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_SATURDAY.atTime(11, 30));

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Bookings on Saturdays must be between 8 AM and 11 AM");
    }

    // ---------- criação bem-sucedida / atribuição de funcionário ----------

    @Test
    void createBookingAssignsFirstFreeStaffAndPublishesEvents() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER"))
                .thenReturn(List.of(staff("Ciclana"), staff("Beltrano")));
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createBooking(dto);

        assertThat(result.getEmployeeName()).isEqualTo("Ciclana");
        verify(notificationPortOut).sendBookingScheduled(result);
        verify(bookingHistoryPortOut).publishBookingScheduled(any());
    }

    @Test
    void createBookingSkipsAlreadyAssignedStaff() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER"))
                .thenReturn(List.of(staff("Ciclana"), staff("Beltrano")));
        var occupied = new Booking();
        occupied.setEmployeeName("Ciclana");
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of(occupied));
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createBooking(dto);

        assertThat(result.getEmployeeName()).isEqualTo("Beltrano");
    }

    @Test
    void createBookingThrowsBusinessRuleExceptionWhenNoStaffScheduled() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER")).thenReturn(List.of());
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Não há funcionário disponível");

        verify(bookingPortOut, never()).save(any());
    }

    @Test
    void createBookingThrowsBusinessRuleExceptionWhenAllStaffBusy() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER")).thenReturn(List.of(staff("Ciclana")));
        var occupied = new Booking();
        occupied.setEmployeeName("Ciclana");
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of(occupied));

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---------- sugestão de próximo horário disponível ----------

    @Test
    void createBookingSuggestsNextAvailableSlotSameDayWhenConflictOccurs() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var suggested = FAR_FUTURE_MONDAY.atTime(9, 30);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER")).thenReturn(List.of());
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());
        when(availableSlotsPortIn.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY))
                .thenReturn(List.of(new AvailableSlot(suggested, 1)));

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(BookingUnavailableException.class)
                .hasMessageContaining("Não há funcionário disponível")
                .hasMessageContaining("Próximo horário disponível no mesmo dia")
                .extracting(ex -> ((BookingUnavailableException) ex).getSuggestedSlot())
                .isEqualTo(suggested);
    }

    @Test
    void createBookingHasNoSuggestionWhenNoOtherSlotIsFreeThatDay() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER")).thenReturn(List.of());
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());
        when(availableSlotsPortIn.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(BookingUnavailableException.class)
                .hasMessageContaining("Não há mais horários disponíveis")
                .extracting(ex -> ((BookingUnavailableException) ex).getSuggestedSlot())
                .isNull();
    }

    @Test
    void createBookingIgnoresSuggestedSlotsAtOrBeforeTheRequestedTime() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 30);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER")).thenReturn(List.of());
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());
        // O próprio slot pedido (e um anterior) aparecem na consulta de
        // disponibilidade — não devem ser sugeridos como "próximo".
        when(availableSlotsPortIn.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY))
                .thenReturn(List.of(
                        new AvailableSlot(FAR_FUTURE_MONDAY.atTime(9, 0), 1),
                        new AvailableSlot(dateTime, 1),
                        new AvailableSlot(FAR_FUTURE_MONDAY.atTime(10, 0), 1)));

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(BookingUnavailableException.class)
                .extracting(ex -> ((BookingUnavailableException) ex).getSuggestedSlot())
                .isEqualTo(FAR_FUTURE_MONDAY.atTime(10, 0));
    }

    // ---------- um agendamento por pet por dia ----------

    @Test
    void createBookingThrowsConflictExceptionWhenPetAlreadyHasBookingSameDay() {
        var petId = UUID.randomUUID();
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime, petId);
        var existingBooking = new Booking();
        existingBooking.setId(UUID.randomUUID());
        when(bookingPortOut.findActiveBookingsForPetOnDate(petId, FAR_FUTURE_MONDAY))
                .thenReturn(List.of(existingBooking));

        assertThatThrownBy(() -> service.createBooking(dto))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(FAR_FUTURE_MONDAY.toString());

        verify(bookingPortOut, never()).save(any());
        verify(customerManagementPortOut, never()).getScheduledStaff(any(), any());
    }

    @Test
    void createBookingSucceedsWhenSamePetHasBookingOnADifferentDay() {
        var petId = UUID.randomUUID();
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime, petId);
        when(bookingPortOut.findActiveBookingsForPetOnDate(petId, FAR_FUTURE_MONDAY)).thenReturn(List.of());
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER")).thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createBooking(dto);

        assertThat(result.getPetId()).isEqualTo(petId);
    }

    // ---------- listagem / busca por id ----------

    @Test
    void findBookingsDelegatesToPortOut() {
        var criteria = new BookingSearchCriteriaDto(null, null, null, null, null, null);
        when(bookingPortOut.findByCriteria(criteria)).thenReturn(List.of(new Booking()));

        var result = service.findBookings(criteria);

        assertThat(result).hasSize(1);
    }

    @Test
    void findBookingByIdReturnsBookingWhenFound() {
        var id = UUID.randomUUID();
        var booking = new Booking();
        when(bookingPortOut.findById(id)).thenReturn(Optional.of(booking));

        assertThat(service.findBookingById(id)).isEqualTo(booking);
    }

    @Test
    void findBookingByIdThrowsWhenNotFound() {
        var id = UUID.randomUUID();
        when(bookingPortOut.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findBookingById(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(id.toString());
    }

    // ---------- atualização ----------

    @Test
    void updateBookingCompletesWhenStatusIsCompleted() {
        var id = UUID.randomUUID();
        var booking = new Booking(requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(9, 0)));
        when(bookingPortOut.findById(id)).thenReturn(Optional.of(booking));
        when(bookingPortOut.save(booking)).thenReturn(booking);

        var update = new BookingUpdateDto();
        update.setObservations("Concluído");
        var result = service.updateBooking(id, update, "COMPLETED");

        assertThat(result.getStatus()).isEqualTo(StatusBooking.COMPLETED);
        verify(notificationPortOut).sendBookingCompleted(booking);
        verify(bookingHistoryPortOut).publishBookingCompleted(any());
    }

    @Test
    void updateBookingReassignsStaffWhenDateTimeChanges() {
        var id = UUID.randomUUID();
        var booking = new Booking(requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(9, 0)));
        when(bookingPortOut.findById(id)).thenReturn(Optional.of(booking));
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var newDateTime = FAR_FUTURE_MONDAY.atTime(10, 0);
        when(customerManagementPortOut.getScheduledStaff(newDateTime, "GROOMER"))
                .thenReturn(List.of(staff("Fulana")));
        when(bookingPortOut.findActiveBookingsAt(newDateTime)).thenReturn(List.of());

        var update = new BookingUpdateDto();
        update.setBookingDateTime(newDateTime);
        var result = service.updateBooking(id, update, null);

        assertThat(result.getEmployeeName()).isEqualTo("Fulana");
        assertThat(result.getBookingDateTime()).isEqualTo(newDateTime);
        verify(notificationPortOut).sendBookingScheduled(result);
        verify(bookingHistoryPortOut).publishBookingScheduled(any());
    }

    @Test
    void updateBookingKeepsCurrentServiceTypeWhenNotProvidedOnDateTimeChange() {
        var id = UUID.randomUUID();
        var booking = new Booking(requestDto(ServiceType.CONSULTA_VETERINARIA, FAR_FUTURE_MONDAY.atTime(9, 0)));
        when(bookingPortOut.findById(id)).thenReturn(Optional.of(booking));
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var newDateTime = FAR_FUTURE_MONDAY.atTime(10, 0);
        when(customerManagementPortOut.getScheduledStaff(newDateTime, "VETERINARIAN"))
                .thenReturn(List.of(staff("Dr. Fulano")));
        when(bookingPortOut.findActiveBookingsAt(newDateTime)).thenReturn(List.of());

        var update = new BookingUpdateDto();
        update.setBookingDateTime(newDateTime);
        service.updateBooking(id, update, null);

        verify(customerManagementPortOut).getScheduledStaff(newDateTime, "VETERINARIAN");
    }

    @Test
    void updateBookingWithoutDateTimeChangeSkipsRevalidationAndReassignment() {
        var id = UUID.randomUUID();
        var booking = new Booking(requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(9, 0)));
        booking.setEmployeeName("Ciclana");
        when(bookingPortOut.findById(id)).thenReturn(Optional.of(booking));
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var update = new BookingUpdateDto();
        update.setObservations("Só mudando observação");
        var result = service.updateBooking(id, update, null);

        assertThat(result.getObservations()).isEqualTo("Só mudando observação");
        assertThat(result.getEmployeeName()).isEqualTo("Ciclana");
        verify(customerManagementPortOut, never()).getScheduledStaff(any(), any());
    }

    @Test
    void updateBookingThrowsConflictExceptionWhenMovingToADayWithAnotherBookingForSamePet() {
        var petId = UUID.randomUUID();
        var id = UUID.randomUUID();
        var booking = new Booking(requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(9, 0), petId));
        when(bookingPortOut.findById(id)).thenReturn(Optional.of(booking));

        var newDateTime = FAR_FUTURE_MONDAY.plusDays(1).atTime(9, 0);
        var otherBooking = new Booking();
        otherBooking.setId(UUID.randomUUID());
        when(bookingPortOut.findActiveBookingsForPetOnDate(petId, FAR_FUTURE_MONDAY.plusDays(1)))
                .thenReturn(List.of(otherBooking));

        var update = new BookingUpdateDto();
        update.setBookingDateTime(newDateTime);

        assertThatThrownBy(() -> service.updateBooking(id, update, null))
                .isInstanceOf(ConflictException.class);

        verify(bookingPortOut, never()).save(any());
    }

    @Test
    void updateBookingDoesNotConflictWithItselfWhenMovingTimeWithinTheSameDay() {
        var petId = UUID.randomUUID();
        var id = UUID.randomUUID();
        var booking = new Booking(requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(9, 0), petId));
        booking.setId(id);
        when(bookingPortOut.findById(id)).thenReturn(Optional.of(booking));
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var newDateTime = FAR_FUTURE_MONDAY.atTime(10, 0);
        // A própria consulta de conflito devolve o agendamento sendo
        // atualizado (ele ainda está salvo com a data antiga no mesmo dia) —
        // não deve contar como conflito consigo mesmo.
        when(bookingPortOut.findActiveBookingsForPetOnDate(petId, FAR_FUTURE_MONDAY)).thenReturn(List.of(booking));
        when(customerManagementPortOut.getScheduledStaff(newDateTime, "GROOMER")).thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(newDateTime)).thenReturn(List.of());

        var update = new BookingUpdateDto();
        update.setBookingDateTime(newDateTime);
        var result = service.updateBooking(id, update, null);

        assertThat(result.getBookingDateTime()).isEqualTo(newDateTime);
    }

    // ---------- cancelamento ----------

    @Test
    void cancelBookingSetsStatusAndPublishesEvents() {
        var id = UUID.randomUUID();
        var booking = new Booking(requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(9, 0)));
        when(bookingPortOut.findById(id)).thenReturn(Optional.of(booking));
        when(bookingPortOut.save(booking)).thenReturn(booking);

        var result = service.cancelBooking(id);

        assertThat(result.getStatus()).isEqualTo(StatusBooking.CANCELED);
        verify(notificationPortOut).sendBookingCanceled(booking);
        verify(bookingHistoryPortOut).publishBookingCanceled(any());
    }

    // ---------- confirmação de pagamento ----------

    @Test
    void confirmPaymentUpdatesBookingAndSavesWithoutNotifyingOrPublishingHistory() {
        var id = UUID.randomUUID();
        var booking = new Booking(requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(9, 0)));
        when(bookingPortOut.findById(id)).thenReturn(Optional.of(booking));
        when(bookingPortOut.save(booking)).thenReturn(booking);
        var request = new com.petshop.booking_service.core.port.in.dto.PaymentConfirmationRequestDto();
        request.setPaymentMethod(com.petshop.booking_service.core.domain.enums.PaymentMethod.PIX);

        var result = service.confirmPayment(id, request);

        assertThat(result.getServiceDetails().getPaymentStatus())
                .isEqualTo(com.petshop.booking_service.core.domain.enums.PaymentStatus.COMPLETED);
        assertThat(result.getServiceDetails().getPaymentMethod())
                .isEqualTo(com.petshop.booking_service.core.domain.enums.PaymentMethod.PIX);
        verify(bookingPortOut).save(booking);
        verify(notificationPortOut, never()).sendBookingScheduled(any());
        verify(notificationPortOut, never()).sendBookingCompleted(any());
        verify(bookingHistoryPortOut, never()).publishBookingScheduled(any());
        verify(bookingHistoryPortOut, never()).publishBookingCompleted(any());
    }

    @Test
    void confirmPaymentThrowsWhenBookingNotFound() {
        var id = UUID.randomUUID();
        when(bookingPortOut.findById(id)).thenReturn(Optional.empty());
        var request = new com.petshop.booking_service.core.port.in.dto.PaymentConfirmationRequestDto();
        request.setPaymentMethod(com.petshop.booking_service.core.domain.enums.PaymentMethod.DINHEIRO);

        assertThatThrownBy(() -> service.confirmPayment(id, request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(bookingPortOut, never()).save(any());
    }
}
