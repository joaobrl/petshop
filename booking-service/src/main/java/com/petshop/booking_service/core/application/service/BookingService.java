package com.petshop.booking_service.core.application.service;

import com.petshop.booking_service.core.domain.AvailableSlot;
import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.StaffAssignment;
import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.domain.enums.StatusBooking;
import com.petshop.booking_service.core.domain.exception.BookingUnavailableException;
import com.petshop.booking_service.core.port.in.AvailableSlotsPortIn;
import com.petshop.booking_service.core.port.in.BookingPortIn;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.core.port.in.dto.BookingUpdateDto;
import com.petshop.booking_service.core.port.in.dto.PaymentConfirmationRequestDto;
import com.petshop.booking_service.core.port.out.BookingHistoryPortOut;
import com.petshop.booking_service.core.port.out.BookingPortOut;
import com.petshop.booking_service.core.port.out.CustomerManagementPortOut;
import com.petshop.booking_service.core.port.out.NotificationPortOut;
import com.petshop.booking_service.core.port.out.dto.BookingResponseDto;
import com.petshop.commons.exception.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.petshop.booking_service.core.domain.enums.StatusBooking.CANCELED;

@Service
@RequiredArgsConstructor
public class BookingService implements BookingPortIn {

    private final BookingPortOut bookingPortOut;
    private final CustomerManagementPortOut customerManagementPortOut;
    private final NotificationPortOut notificationPortOut;
    private final BookingHistoryPortOut bookingHistoryPortOut;
    private final AvailableSlotsPortIn availableSlotsPortIn;

    @Override
    @Transactional
    @CacheEvict(cacheNames = "availableSlots", allEntries = true)
    public Booking createBooking(BookingRequestDto bookingRequest) {
        validateBookingTime(bookingRequest.getBookingDateTime(), bookingRequest.getServiceType());

        validateNoConflictingPetBooking(
                UUID.fromString(bookingRequest.getPetId()), bookingRequest.getBookingDateTime(), null);
        var employeeName = ensureAvailabilityAndAssignEmployee(
                bookingRequest.getBookingDateTime(), bookingRequest.getServiceType());

        var booking = new Booking(bookingRequest);
        booking.setEmployeeName(employeeName);
        var savedBooking = bookingPortOut.save(booking);
        var dto = new BookingResponseDto(savedBooking);

        notificationPortOut.sendBookingScheduled(savedBooking);
        bookingHistoryPortOut.publishBookingScheduled(dto);

        return savedBooking;
    }

   
    @Override
    public Page<Booking> findBookings(BookingSearchCriteriaDto criteria, Pageable pageable){
        return bookingPortOut.findByCriteria(criteria, pageable);
    }

    @Override
    public Booking findBookingById(UUID id) {
        return bookingPortOut.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Booking not found with id: " + id));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "availableSlots", allEntries = true)
    public Booking updateBooking(UUID id, BookingUpdateDto bookingUpdate, String status) {
        var booking = findBookingById(id);

        if (StatusBooking.COMPLETED.name().equalsIgnoreCase(status)) {
            booking.completeBooking(bookingUpdate);
            var saved = bookingPortOut.save(booking);

            var dto = new BookingResponseDto(saved);
            notificationPortOut.sendBookingCompleted(saved);
            bookingHistoryPortOut.publishBookingCompleted(dto);
            return saved;
        }

        if (bookingUpdate.getBookingDateTime() != null) {
            var effectiveServiceType = bookingUpdate.getServiceType() != null
                    ? bookingUpdate.getServiceType()
                    : booking.getServiceDetails().getServiceType();
            validateBookingTime(bookingUpdate.getBookingDateTime(), effectiveServiceType);

            validateNoConflictingPetBooking(booking.getPetId(), bookingUpdate.getBookingDateTime(), id);
            var employeeName = ensureAvailabilityAndAssignEmployee(bookingUpdate.getBookingDateTime(), effectiveServiceType);
            booking.setEmployeeName(employeeName);
        }

        booking.update(bookingUpdate);
        var saved = bookingPortOut.save(booking);

        var dto = new BookingResponseDto(saved);
        notificationPortOut.sendBookingScheduled(saved);
        bookingHistoryPortOut.publishBookingScheduled(dto);

        return saved;
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = "availableSlots", allEntries = true)
    public Booking cancelBooking(UUID id) {
        var booking = findBookingById(id);
        booking.setStatus(CANCELED);
        var saved = bookingPortOut.save(booking);

        var dto = new BookingResponseDto(saved);
        notificationPortOut.sendBookingCanceled(saved);
        bookingHistoryPortOut.publishBookingCanceled(dto);

        return saved;
    }

    @Override
    @Transactional
    public Booking confirmPayment(UUID id, PaymentConfirmationRequestDto request) {
        var booking = findBookingById(id);
        booking.confirmPayment(request.getPaymentMethod());
        return bookingPortOut.save(booking);
    }

    private void validateNoConflictingPetBooking(UUID petId, LocalDateTime bookingDateTime, UUID excludeBookingId) {
        var conflict = bookingPortOut.findActiveBookingsForPetOnDate(petId, bookingDateTime.toLocalDate()).stream()
                .anyMatch(b -> excludeBookingId == null || !b.getId().equals(excludeBookingId));
        if (conflict) {
            throw new ConflictException("Pet already has a booking scheduled for " + bookingDateTime.toLocalDate());
        }
    }

    private void validateBookingTime(LocalDateTime bookingDateTime, ServiceType serviceType) {
        if (bookingDateTime == null) throw new IllegalArgumentException("Booking date and time cannot be null");
        if (serviceType == null) throw new IllegalArgumentException("Service type cannot be null");

        var time = bookingDateTime.toLocalTime();
        var dayOfWeek = bookingDateTime.getDayOfWeek();
        var isVet = serviceType == ServiceType.CONSULTA_VETERINARIA;

        if (dayOfWeek == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException("The store is closed on Sundays");
        }

        if (dayOfWeek == DayOfWeek.SATURDAY && isVet) {
            throw new IllegalArgumentException("Veterinary consultations are only available Monday through Friday");
        }

        if (dayOfWeek != DayOfWeek.SATURDAY) {
            boolean outsideWindow = time.isBefore(LocalTime.of(9, 0)) || !time.isBefore(LocalTime.of(17, 0));
            boolean duringLunch = !time.isBefore(LocalTime.of(12, 0)) && time.isBefore(LocalTime.of(13, 0));
            if (outsideWindow || duringLunch) {
                throw new IllegalArgumentException("Bookings on weekdays must be between 9 AM and 5 PM, with a break from 12 PM to 1 PM");
            }
        } else {
            if (time.isBefore(LocalTime.of(8, 0)) || !time.isBefore(LocalTime.of(11, 0))) {
                throw new IllegalArgumentException("Bookings on Saturdays must be between 8 AM and 11 AM");
            }
        }

        var now = LocalDateTime.now();
        var twoHoursFromNow = now.plusHours(2);

        if (!bookingDateTime.isAfter(twoHoursFromNow)) {
            throw new IllegalArgumentException("Booking must be made at least 2 hours in advance");
        }
    }

    private String ensureAvailabilityAndAssignEmployee(LocalDateTime bookingDateTime, ServiceType serviceType) {
        var scheduledStaff = customerManagementPortOut.getScheduledStaff(bookingDateTime, serviceType.requiredStaffRole());

        var alreadyAssigned = bookingPortOut.findActiveBookingsAt(bookingDateTime).stream()
                .map(Booking::getEmployeeName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return scheduledStaff.stream()
                .map(StaffAssignment::name)
                .filter(name -> !alreadyAssigned.contains(name))
                .findFirst()
                .orElseThrow(() -> noEmployeeAvailable(bookingDateTime, serviceType));
    }

    private static final DateTimeFormatter SLOT_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_ONLY_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private BookingUnavailableException noEmployeeAvailable(LocalDateTime requestedDateTime, ServiceType serviceType) {
        var suggestion = findNextAvailableSlotSameDay(requestedDateTime, serviceType);
        var prefix = "Não há funcionário disponível para o horário selecionado: " + requestedDateTime;

        if (suggestion != null) {
            var message = prefix + ". Próximo horário disponível no mesmo dia: "
                    + suggestion.format(SLOT_FORMATTER) + ".";
            return new BookingUnavailableException(message, suggestion);
        }

        var message = prefix + ". Não há mais horários disponíveis para o dia "
                + requestedDateTime.toLocalDate().format(DATE_ONLY_FORMATTER) + ".";
        return new BookingUnavailableException(message, null);
    }

    private LocalDateTime findNextAvailableSlotSameDay(LocalDateTime requestedDateTime, ServiceType serviceType) {
        return availableSlotsPortIn.findAvailableSlots(serviceType, requestedDateTime.toLocalDate(), RangeType.DAY)
                .stream()
                .map(AvailableSlot::dateTime)
                .filter(dateTime -> dateTime.isAfter(requestedDateTime))
                .findFirst()
                .orElse(null);
    }

}