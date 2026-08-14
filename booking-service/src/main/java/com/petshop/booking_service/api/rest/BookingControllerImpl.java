package com.petshop.booking_service.api.rest;

import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.booking_service.core.port.in.BookingPortIn;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.core.port.in.dto.BookingUpdateDto;
import com.petshop.booking_service.core.port.in.dto.PaymentConfirmationRequestDto;
import com.petshop.booking_service.core.port.out.dto.BookingResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BookingControllerImpl implements BookingController {

    private final BookingPortIn bookingPortIn;

    @Override
    public ResponseEntity<BookingResponseDto> createBooking(BookingRequestDto bookingRequest, UriComponentsBuilder uriBuilder, AuthenticatedUser user) {
        applyOwnerIdentity(bookingRequest, user);
        var booking = bookingPortIn.createBooking(bookingRequest);
        var uri = uriBuilder.path("/bookings/{id}").buildAndExpand(booking.getId()).toUri();
        return ResponseEntity.created(uri).body(new BookingResponseDto(booking));
    }

    private void applyOwnerIdentity(BookingRequestDto request, AuthenticatedUser user) {
        if (user == null || user.type() != AccountType.CUSTOMER) {
            return;
        }
        request.setOwnerName(user.name());
        request.setOwnerCpf(user.cpf());
        request.setOwnerContact(user.phone());
        request.setOwnerEmail(user.email());
    }

    @Override
    public ResponseEntity<List<BookingResponseDto>> listBookings(String ownerCpf, UUID petId, LocalDate date, String employeeName, String serviceType, String status, AuthenticatedUser user) {

        String effectiveOwnerCpf = (user != null && user.type() == AccountType.CUSTOMER)
                ? user.cpf()
                : ownerCpf;
        var criteria = new BookingSearchCriteriaDto(effectiveOwnerCpf, petId, serviceType, status, date, employeeName);
        var bookings = bookingPortIn.findBookings(criteria)
                .stream()
                .map(BookingResponseDto::new)
                .toList();
        return ResponseEntity.ok(bookings);
    }

    @Override
    public ResponseEntity<BookingResponseDto> getFindBookingById(UUID id, AuthenticatedUser user) {
        var booking = bookingPortIn.findBookingById(id);
        requireOwnershipIfCustomer(booking.getOwnerCpf(), user);
        return ResponseEntity.ok(new BookingResponseDto(booking));
    }

    private void requireOwnershipIfCustomer(String bookingOwnerCpf, AuthenticatedUser user) {
        if (user == null || user.type() != AccountType.CUSTOMER) {
            return;
        }
        if (user.cpf() == null || !user.cpf().equals(bookingOwnerCpf)) {
            throw new AccessDeniedException("Cliente só pode acessar os próprios agendamentos.");
        }
    }

    @Override
    public ResponseEntity<BookingResponseDto> updateBooking(UUID id, BookingUpdateDto bookingUpdate, String status, AuthenticatedUser user) {
        requireOwnershipIfCustomer(bookingPortIn.findBookingById(id).getOwnerCpf(), user);
        var booking = bookingPortIn.updateBooking(id, bookingUpdate, status);
        return ResponseEntity.ok(new BookingResponseDto(booking));
    }

    @Override
    public ResponseEntity<BookingResponseDto> cancelBooking(UUID id, AuthenticatedUser user) {
        requireOwnershipIfCustomer(bookingPortIn.findBookingById(id).getOwnerCpf(), user);
        var booking = bookingPortIn.cancelBooking(id);
        return ResponseEntity.ok(new BookingResponseDto(booking));
    }

    @Override
    public ResponseEntity<BookingResponseDto> confirmPayment(UUID id, PaymentConfirmationRequestDto request, AuthenticatedUser user) {
        var booking = bookingPortIn.confirmPayment(id, request);
        return ResponseEntity.ok(new BookingResponseDto(booking));
    }

}
