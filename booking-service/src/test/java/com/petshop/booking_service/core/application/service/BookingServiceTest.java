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
import com.petshop.commons.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
        service = new BookingService(bookingPortOut, customerManagementPortOut, notificationPortOut, bookingHistoryPortOut, availableSlotsPortIn, Clock.systemDefaultZone());
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

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Data e hora do agendamento não podem ser nulas");
    }

    @Test
    void rejectsNullServiceType() {
        var dto = requestDto(null, FAR_FUTURE_MONDAY.atTime(9, 0));

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Tipo de serviço não pode ser nulo");
    }

    @Test
    void rejectsSundayBooking() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_SUNDAY.atTime(9, 0));

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A loja não funciona aos domingos");
    }

    @Test
    void rejectsVeterinaryConsultationOnSaturday() {
        var dto = requestDto(ServiceType.CONSULTA_VETERINARIA, FAR_FUTURE_SATURDAY.atTime(9, 0));

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Consultas veterinárias só estão disponíveis de segunda a sexta-feira");
    }

    @Test
    void rejectsWeekdayBookingBeforeOpeningHours() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(8, 0));

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Agendamentos em dias úteis devem ser entre 9h e 17h, com intervalo de almoço entre 12h e 13h");
    }

    @Test
    void rejectsWeekdayBookingDuringLunchBreak() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(12, 30));

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Agendamentos em dias úteis devem ser entre 9h e 17h, com intervalo de almoço entre 12h e 13h");
    }

    @Test
    void rejectsWeekdayBookingAfterClosingHours() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(17, 30));

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Agendamentos em dias úteis devem ser entre 9h e 17h, com intervalo de almoço entre 12h e 13h");
    }

    @Test
    void rejectsSaturdayBookingOutsideAllowedHours() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_SATURDAY.atTime(11, 30));

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Agendamentos aos sábados devem ser entre 8h e 11h");
    }

    // ---------- ADMIN/RECEPTIONIST continuam sujeitos ao horário de funcionamento ----------
    // (só a antecedência mínima de 2h é dispensada — ver o bloco de testes mais abaixo)

    @Test
    void adminStillSubjectToSundayRuleOnCreate() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_SUNDAY.atTime(9, 0));

        assertThatThrownBy(() -> service.createBooking(dto, Role.ADMIN, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A loja não funciona aos domingos");
    }

    @Test
    void receptionistStillSubjectToOpeningHoursRuleOnCreate() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(7, 0));

        assertThatThrownBy(() -> service.createBooking(dto, Role.RECEPTIONIST, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Agendamentos em dias úteis devem ser entre 9h e 17h, com intervalo de almoço entre 12h e 13h");
    }

    @Test
    void adminStillRequiresAvailableStaffOnCreate() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER", null)).thenReturn(List.of());
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createBooking(dto, Role.ADMIN, null))
                .isInstanceOf(BookingUnavailableException.class);

        verify(bookingPortOut, never()).save(any());
    }

    @Test
    void customerRoleDoesNotBypassBusinessHoursRuleOnCreate() {
        var dto = requestDto(ServiceType.BANHO, FAR_FUTURE_SUNDAY.atTime(9, 0));

        assertThatThrownBy(() -> service.createBooking(dto, Role.CUSTOMER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A loja não funciona aos domingos");
    }

    // ---------- ADMIN/RECEPTIONIST dispensam só a antecedência mínima de 2h ----------
    // Precisa de um Clock fixo: a regra compara bookingDateTime com "agora", então sem controlar
    // "agora" não há como testar de forma determinística um agendamento a menos de 2h de distância.

    private BookingService serviceWithFixedClock(LocalDateTime fixedNow) {
        var zone = ZoneId.systemDefault();
        var clock = Clock.fixed(fixedNow.atZone(zone).toInstant(), zone);
        return new BookingService(bookingPortOut, customerManagementPortOut, notificationPortOut, bookingHistoryPortOut, availableSlotsPortIn, clock);
    }

    @Test
    void adminBypassesMinimumAdvanceNoticeOnCreate() {
        var fixedNow = FAR_FUTURE_MONDAY.atTime(10, 0);
        var bookingDateTime = fixedNow.plusMinutes(30);
        var fixedClockService = serviceWithFixedClock(fixedNow);
        var dto = requestDto(ServiceType.BANHO, bookingDateTime);
        when(customerManagementPortOut.getScheduledStaff(bookingDateTime, "GROOMER", null)).thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(bookingDateTime)).thenReturn(List.of());
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = fixedClockService.createBooking(dto, Role.ADMIN, null);

        assertThat(result.getEmployeeName()).isEqualTo("Ciclana");
    }

    @Test
    void receptionistBypassesMinimumAdvanceNoticeOnCreate() {
        var fixedNow = FAR_FUTURE_MONDAY.atTime(10, 0);
        var bookingDateTime = fixedNow.plusMinutes(30);
        var fixedClockService = serviceWithFixedClock(fixedNow);
        var dto = requestDto(ServiceType.BANHO, bookingDateTime);
        when(customerManagementPortOut.getScheduledStaff(bookingDateTime, "GROOMER", null)).thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(bookingDateTime)).thenReturn(List.of());
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = fixedClockService.createBooking(dto, Role.RECEPTIONIST, null);

        assertThat(result.getEmployeeName()).isEqualTo("Ciclana");
    }

    @Test
    void customerRoleIsStillBlockedByMinimumAdvanceNoticeOnCreate() {
        var fixedNow = FAR_FUTURE_MONDAY.atTime(10, 0);
        var bookingDateTime = fixedNow.plusMinutes(30);
        var fixedClockService = serviceWithFixedClock(fixedNow);
        var dto = requestDto(ServiceType.BANHO, bookingDateTime);

        assertThatThrownBy(() -> fixedClockService.createBooking(dto, Role.CUSTOMER, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("O agendamento deve ser feito com pelo menos 2 horas de antecedência");
    }

    @Test
    void adminBypassesMinimumAdvanceNoticeOnUpdate() {
        var id = UUID.randomUUID();
        var booking = new Booking(requestDto(ServiceType.BANHO, FAR_FUTURE_MONDAY.atTime(9, 0)));
        when(bookingPortOut.findById(id)).thenReturn(Optional.of(booking));
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var fixedNow = FAR_FUTURE_MONDAY.atTime(10, 0);
        var newDateTime = fixedNow.plusMinutes(30);
        var fixedClockService = serviceWithFixedClock(fixedNow);
        when(customerManagementPortOut.getScheduledStaff(newDateTime, "GROOMER", null)).thenReturn(List.of(staff("Fulana")));
        when(bookingPortOut.findActiveBookingsAt(newDateTime)).thenReturn(List.of());

        var update = new BookingUpdateDto();
        update.setBookingDateTime(newDateTime);
        var result = fixedClockService.updateBooking(id, update, null, Role.ADMIN, null);

        assertThat(result.getEmployeeName()).isEqualTo("Fulana");
        assertThat(result.getBookingDateTime()).isEqualTo(newDateTime);
    }

    @Test
    void receptionistCanCreateSecondBookingSameDayWhenExistingBookingForPetIsCompleted() {
        var petId = UUID.randomUUID();
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime, petId);
        var completedBooking = new Booking();
        completedBooking.setId(UUID.randomUUID());
        completedBooking.setStatus(StatusBooking.COMPLETED);
        when(bookingPortOut.findActiveBookingsForPetOnDate(petId, FAR_FUTURE_MONDAY))
                .thenReturn(List.of(completedBooking));
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER", null)).thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createBooking(dto, Role.RECEPTIONIST, null);

        assertThat(result.getPetId()).isEqualTo(petId);
    }

    @Test
    void adminIsStillBlockedWhenPetHasPendingBookingSameDay() {
        var petId = UUID.randomUUID();
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime, petId);
        var pendingBooking = new Booking();
        pendingBooking.setId(UUID.randomUUID());
        pendingBooking.setStatus(StatusBooking.SCHEDULED);
        when(bookingPortOut.findActiveBookingsForPetOnDate(petId, FAR_FUTURE_MONDAY))
                .thenReturn(List.of(pendingBooking));

        assertThatThrownBy(() -> service.createBooking(dto, Role.ADMIN, null))
                .isInstanceOf(ConflictException.class);

        verify(bookingPortOut, never()).save(any());
    }

    @Test
    void customerRoleIsStillBlockedByCompletedBookingSameDay() {
        var petId = UUID.randomUUID();
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime, petId);
        var completedBooking = new Booking();
        completedBooking.setId(UUID.randomUUID());
        completedBooking.setStatus(StatusBooking.COMPLETED);
        when(bookingPortOut.findActiveBookingsForPetOnDate(petId, FAR_FUTURE_MONDAY))
                .thenReturn(List.of(completedBooking));

        assertThatThrownBy(() -> service.createBooking(dto, Role.CUSTOMER, null))
                .isInstanceOf(ConflictException.class);

        verify(bookingPortOut, never()).save(any());
    }

    // ---------- criação bem-sucedida / atribuição de funcionário ----------

    @Test
    void createBookingAssignsFirstFreeStaffAndPublishesEvents() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER", null))
                .thenReturn(List.of(staff("Ciclana"), staff("Beltrano")));
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createBooking(dto, null, null);

        assertThat(result.getEmployeeName()).isEqualTo("Ciclana");
        verify(notificationPortOut).sendBookingScheduled(result);
        verify(bookingHistoryPortOut).publishBookingScheduled(any());
    }

    @Test
    void createBookingSkipsAlreadyAssignedStaff() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER", null))
                .thenReturn(List.of(staff("Ciclana"), staff("Beltrano")));
        var occupied = new Booking();
        occupied.setEmployeeName("Ciclana");
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of(occupied));
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createBooking(dto, null, null);

        assertThat(result.getEmployeeName()).isEqualTo("Beltrano");
    }

    @Test
    void createBookingThrowsBusinessRuleExceptionWhenNoStaffScheduled() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER", null)).thenReturn(List.of());
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("Não há funcionário disponível");

        verify(bookingPortOut, never()).save(any());
    }

    @Test
    void createBookingThrowsBusinessRuleExceptionWhenAllStaffBusy() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER", null)).thenReturn(List.of(staff("Ciclana")));
        var occupied = new Booking();
        occupied.setEmployeeName("Ciclana");
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of(occupied));

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    // ---------- sugestão de próximo horário disponível ----------

    @Test
    void createBookingSuggestsNextAvailableSlotSameDayWhenConflictOccurs() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var suggested = FAR_FUTURE_MONDAY.atTime(9, 30);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER", null)).thenReturn(List.of());
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());
        when(availableSlotsPortIn.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY))
                .thenReturn(List.of(new AvailableSlot(suggested, 1)));

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
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
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER", null)).thenReturn(List.of());
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());
        when(availableSlotsPortIn.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(BookingUnavailableException.class)
                .hasMessageContaining("Não há mais horários disponíveis")
                .extracting(ex -> ((BookingUnavailableException) ex).getSuggestedSlot())
                .isNull();
    }

    @Test
    void createBookingIgnoresSuggestedSlotsAtOrBeforeTheRequestedTime() {
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 30);
        var dto = requestDto(ServiceType.BANHO, dateTime);
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER", null)).thenReturn(List.of());
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());
        // O próprio slot pedido (e um anterior) aparecem na consulta de
        // disponibilidade — não devem ser sugeridos como "próximo".
        when(availableSlotsPortIn.findAvailableSlots(ServiceType.BANHO, FAR_FUTURE_MONDAY, RangeType.DAY))
                .thenReturn(List.of(
                        new AvailableSlot(FAR_FUTURE_MONDAY.atTime(9, 0), 1),
                        new AvailableSlot(dateTime, 1),
                        new AvailableSlot(FAR_FUTURE_MONDAY.atTime(10, 0), 1)));

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
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

        assertThatThrownBy(() -> service.createBooking(dto, null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(FAR_FUTURE_MONDAY.toString());

        verify(bookingPortOut, never()).save(any());
        verify(customerManagementPortOut, never()).getScheduledStaff(any(), any(), any());
    }

    @Test
    void createBookingSucceedsWhenSamePetHasBookingOnADifferentDay() {
        var petId = UUID.randomUUID();
        var dateTime = FAR_FUTURE_MONDAY.atTime(9, 0);
        var dto = requestDto(ServiceType.BANHO, dateTime, petId);
        when(bookingPortOut.findActiveBookingsForPetOnDate(petId, FAR_FUTURE_MONDAY)).thenReturn(List.of());
        when(customerManagementPortOut.getScheduledStaff(dateTime, "GROOMER", null)).thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(dateTime)).thenReturn(List.of());
        when(bookingPortOut.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.createBooking(dto, null, null);

        assertThat(result.getPetId()).isEqualTo(petId);
    }

    // ---------- listagem / busca por id ----------

    @Test
    void findBookingsDelegatesToPortOut() {
        var criteria = new BookingSearchCriteriaDto(null, null, null, null, null, null);
        var pageable = PageRequest.of(0, 20);
        when(bookingPortOut.findByCriteria(criteria, pageable)).thenReturn(new PageImpl<>(List.of(new Booking())));

        var result = service.findBookings(criteria, pageable);

        assertThat(result.getContent()).hasSize(1);
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
        var result = service.updateBooking(id, update, "COMPLETED", null, null);

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
        when(customerManagementPortOut.getScheduledStaff(newDateTime, "GROOMER", null))
                .thenReturn(List.of(staff("Fulana")));
        when(bookingPortOut.findActiveBookingsAt(newDateTime)).thenReturn(List.of());

        var update = new BookingUpdateDto();
        update.setBookingDateTime(newDateTime);
        var result = service.updateBooking(id, update, null, null, null);

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
        when(customerManagementPortOut.getScheduledStaff(newDateTime, "VETERINARIAN", null))
                .thenReturn(List.of(staff("Dr. Fulano")));
        when(bookingPortOut.findActiveBookingsAt(newDateTime)).thenReturn(List.of());

        var update = new BookingUpdateDto();
        update.setBookingDateTime(newDateTime);
        service.updateBooking(id, update, null, null, null);

        verify(customerManagementPortOut).getScheduledStaff(newDateTime, "VETERINARIAN", null);
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
        var result = service.updateBooking(id, update, null, null, null);

        assertThat(result.getObservations()).isEqualTo("Só mudando observação");
        assertThat(result.getEmployeeName()).isEqualTo("Ciclana");
        verify(customerManagementPortOut, never()).getScheduledStaff(any(), any(), any());
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

        assertThatThrownBy(() -> service.updateBooking(id, update, null, null, null))
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
        when(customerManagementPortOut.getScheduledStaff(newDateTime, "GROOMER", null)).thenReturn(List.of(staff("Ciclana")));
        when(bookingPortOut.findActiveBookingsAt(newDateTime)).thenReturn(List.of());

        var update = new BookingUpdateDto();
        update.setBookingDateTime(newDateTime);
        var result = service.updateBooking(id, update, null, null, null);

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
