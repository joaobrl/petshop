package com.petshop.booking_service.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.booking_service.core.port.in.BookingPortIn;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto;
import com.petshop.booking_service.core.port.in.dto.BookingUpdateDto;
import com.petshop.booking_service.core.port.in.dto.PaymentConfirmationRequestDto;
import com.petshop.booking_service.core.port.out.dto.BookingResponseDto;
import com.petshop.commons.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class BookingControllerImpl implements BookingController {

    private final BookingPortIn bookingPortIn;
    private final JwtService jwtService;

    @Override
    public ResponseEntity<BookingResponseDto> createBooking(BookingRequestDto bookingRequest, UriComponentsBuilder uriBuilder, String token) {
        var user = jwtService.parseAuthorizationHeaderOrNull(token);
        applyOwnerIdentity(bookingRequest, user);
        var booking = bookingPortIn.createBooking(bookingRequest, user != null ? user.role() : null, token);
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
    public ResponseEntity<PageResponse<BookingResponseDto>> listBookings(String ownerCpf, UUID petId, LocalDate date, String employeeName, String serviceType, String status, Pageable pageable, String token) {
        var user = jwtService.parseAuthorizationHeaderOrNull(token);

        String effectiveOwnerCpf = (user != null && user.type() == AccountType.CUSTOMER)
                ? user.cpf()
                : ownerCpf;
        var criteria = new BookingSearchCriteriaDto(effectiveOwnerCpf, petId, serviceType, status, date, employeeName);
        var page = bookingPortIn.findBookings(criteria, pageable).map(BookingResponseDto::new);
        return ResponseEntity.ok(new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast()));
    }

    @Override
    public ResponseEntity<BookingResponseDto> getFindBookingById(UUID id, String token) {
        var user = jwtService.parseAuthorizationHeaderOrNull(token);
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
    public ResponseEntity<BookingResponseDto> updateBooking(UUID id, BookingUpdateDto bookingUpdate, String status, String token) {
        var user = jwtService.parseAuthorizationHeaderOrNull(token);
        requireOwnershipIfCustomer(bookingPortIn.findBookingById(id).getOwnerCpf(), user);
        var booking = bookingPortIn.updateBooking(id, bookingUpdate, status, user != null ? user.role() : null, token);
        return ResponseEntity.ok(new BookingResponseDto(booking));
    }

    @Override
    public ResponseEntity<BookingResponseDto> cancelBooking(UUID id, String token) {
        var user = jwtService.parseAuthorizationHeaderOrNull(token);
        requireOwnershipIfCustomer(bookingPortIn.findBookingById(id).getOwnerCpf(), user);
        var booking = bookingPortIn.cancelBooking(id);
        return ResponseEntity.ok(new BookingResponseDto(booking));
    }

    @Override
    public ResponseEntity<BookingResponseDto> confirmPayment(UUID id, PaymentConfirmationRequestDto request, String token) {
        var booking = bookingPortIn.confirmPayment(id, request);
        return ResponseEntity.ok(new BookingResponseDto(booking));
    }

}
