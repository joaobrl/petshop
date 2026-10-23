package com.petshop.booking_service.api.rest;

import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.booking_service.core.port.in.dto.BookingUpdateDto;
import com.petshop.booking_service.core.port.in.dto.PaymentConfirmationRequestDto;
import com.petshop.booking_service.core.port.out.dto.BookingResponseDto;
import com.petshop.commons.dto.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.UUID;

@RequestMapping("/api/v1/bookings")
public interface BookingController {

    @PostMapping("/create")
    ResponseEntity<BookingResponseDto> createBooking(
            @Valid @RequestBody BookingRequestDto bookingRequest,
            UriComponentsBuilder uriBuilder,
            @RequestHeader("Authorization") String token);


    @GetMapping("/list")
    ResponseEntity<PageResponse<BookingResponseDto>> listBookings(@RequestParam(required = false) String ownerCpf,
                                                          @RequestParam(required = false) UUID petId,
                                                          @RequestParam(required = false) LocalDate date,
                                                          @RequestParam(required = false) String employeeName,
                                                          @RequestParam(required = false) String serviceType,
                                                          @RequestParam(required = false) String status,
                                                          @PageableDefault(size = 20) Pageable pageable,
                                                          @RequestHeader("Authorization") String token);

    @GetMapping("/find/{id}")
    ResponseEntity<BookingResponseDto> getFindBookingById(@PathVariable UUID id, @RequestHeader("Authorization") String token);

    @PatchMapping("/update/{id}")
    ResponseEntity<BookingResponseDto> updateBooking(@PathVariable UUID id, @Valid @RequestBody BookingUpdateDto bookingUpdate, @RequestParam(required = false) String status, @RequestHeader("Authorization") String token);

    @DeleteMapping("/cancel/{id}")
    ResponseEntity<BookingResponseDto> cancelBooking(@PathVariable UUID id, @RequestHeader("Authorization") String token);

    @PatchMapping("/confirm-payment/{id}")
    ResponseEntity<BookingResponseDto> confirmPayment(
            @PathVariable UUID id,
            @Valid @RequestBody PaymentConfirmationRequestDto request,
            @RequestHeader("Authorization") String token);

}
