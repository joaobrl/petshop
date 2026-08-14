package com.petshop.booking_service.api.rest;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.port.in.BookingPortIn;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.booking_service.core.port.in.dto.BookingUpdateDto;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingControllerImplTest {

    @Mock
    private BookingPortIn bookingPortIn;

    private BookingControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new BookingControllerImpl(bookingPortIn);
    }

    private AuthenticatedUser customer(String cpf) {
        return new AuthenticatedUser(UUID.randomUUID(), "cliente@petshop.com", "Cliente Nome", cpf,
                "11999990000", Role.CUSTOMER, AccountType.CUSTOMER);
    }

    private AuthenticatedUser receptionist() {
        return new AuthenticatedUser(UUID.randomUUID(), "recepcao@petshop.com", "Recepcao", "00000000000",
                "11988887777", Role.RECEPTIONIST, AccountType.STAFF);
    }

    private BookingRequestDto requestDto() {
        var dto = new BookingRequestDto();
        dto.setOwnerName("Nome Original");
        dto.setOwnerCpf("00000000000");
        dto.setOwnerContact("00000000000");
        dto.setOwnerEmail("original@petshop.com");
        dto.setServiceType(ServiceType.BANHO);
        dto.setBookingDateTime(LocalDateTime.of(2026, 8, 15, 9, 0));
        return dto;
    }

    private Booking bookingWithOwnerCpf(String cpf) {
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setOwnerCpf(cpf);
        booking.setServiceDetails(new com.petshop.booking_service.core.domain.ServiceDetails(ServiceType.BANHO));
        return booking;
    }

    @Test
    void createBookingOverridesOwnerIdentityForCustomer() {
        var user = customer("12345678900");
        var request = requestDto();
        var savedBooking = bookingWithOwnerCpf("12345678900");
        when(bookingPortIn.createBooking(request)).thenReturn(savedBooking);

        var response = controller.createBooking(request, UriComponentsBuilder.newInstance(), user);

        assertThat(request.getOwnerName()).isEqualTo("Cliente Nome");
        assertThat(request.getOwnerCpf()).isEqualTo("12345678900");
        assertThat(request.getOwnerContact()).isEqualTo("11999990000");
        assertThat(request.getOwnerEmail()).isEqualTo("cliente@petshop.com");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(savedBooking.getId());
    }

    @Test
    void createBookingKeepsRequestBodyOwnerForReceptionist() {
        var user = receptionist();
        var request = requestDto();
        when(bookingPortIn.createBooking(request)).thenReturn(bookingWithOwnerCpf("00000000000"));

        controller.createBooking(request, UriComponentsBuilder.newInstance(), user);

        assertThat(request.getOwnerName()).isEqualTo("Nome Original");
        assertThat(request.getOwnerCpf()).isEqualTo("00000000000");
    }

    @Test
    void createBookingKeepsRequestBodyOwnerWhenUserIsNull() {
        var request = requestDto();
        when(bookingPortIn.createBooking(request)).thenReturn(bookingWithOwnerCpf("00000000000"));

        controller.createBooking(request, UriComponentsBuilder.newInstance(), null);

        assertThat(request.getOwnerName()).isEqualTo("Nome Original");
    }

    private final org.springframework.data.domain.Pageable pageable = PageRequest.of(0, 20);

    @Test
    void listBookingsForcesOwnerCpfToTokenForCustomer() {
        var user = customer("12345678900");
        when(bookingPortIn.findBookings(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(new PageImpl<>(List.of()));

        controller.listBookings("outro-cpf-qualquer", null, null, null, null, null, pageable, user);

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto.class);
        org.mockito.Mockito.verify(bookingPortIn).findBookings(captor.capture(), org.mockito.ArgumentMatchers.eq(pageable));
        assertThat(captor.getValue().getOwnerCpf()).isEqualTo("12345678900");
    }

    @Test
    void listBookingsUsesProvidedOwnerCpfForReceptionist() {
        var user = receptionist();
        when(bookingPortIn.findBookings(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(new PageImpl<>(List.of()));

        controller.listBookings("11122233344", null, null, null, null, null, pageable, user);

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto.class);
        org.mockito.Mockito.verify(bookingPortIn).findBookings(captor.capture(), org.mockito.ArgumentMatchers.eq(pageable));
        assertThat(captor.getValue().getOwnerCpf()).isEqualTo("11122233344");
    }

    @Test
    void listBookingsMapsEmployeeNameServiceTypeAndStatusToMatchingCriteriaFields() {
        // Regressão: a ordem dos parâmetros tem que bater com a assinatura em BookingController
        // (employeeName, serviceType, status) — reordenar os três silenciosamente é um erro fácil de cometer.
        var user = receptionist();
        when(bookingPortIn.findBookings(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(new PageImpl<>(List.of()));

        controller.listBookings(null, null, null, "Funcionario Um", ServiceType.BANHO.name(),
                com.petshop.booking_service.core.domain.enums.StatusBooking.SCHEDULED.name(), pageable, user);

        var captor = org.mockito.ArgumentCaptor.forClass(
                com.petshop.booking_service.core.port.in.dto.BookingSearchCriteriaDto.class);
        org.mockito.Mockito.verify(bookingPortIn).findBookings(captor.capture(), org.mockito.ArgumentMatchers.eq(pageable));
        assertThat(captor.getValue().getEmployeeName()).isEqualTo("Funcionario Um");
        assertThat(captor.getValue().getServiceType()).isEqualTo(ServiceType.BANHO);
        assertThat(captor.getValue().getStatus())
                .isEqualTo(com.petshop.booking_service.core.domain.enums.StatusBooking.SCHEDULED);
    }

    @Test
    void getFindBookingByIdAllowsOwnerCustomer() {
        var user = customer("12345678900");
        var booking = bookingWithOwnerCpf("12345678900");
        when(bookingPortIn.findBookingById(booking.getId())).thenReturn(booking);

        var response = controller.getFindBookingById(booking.getId(), user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getFindBookingByIdDeniesNonOwnerCustomer() {
        var user = customer("00000000000");
        var booking = bookingWithOwnerCpf("12345678900");
        when(bookingPortIn.findBookingById(booking.getId())).thenReturn(booking);

        assertThatThrownBy(() -> controller.getFindBookingById(booking.getId(), user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getFindBookingByIdAllowsReceptionistForAnyOwner() {
        var user = receptionist();
        var booking = bookingWithOwnerCpf("12345678900");
        when(bookingPortIn.findBookingById(booking.getId())).thenReturn(booking);

        var response = controller.getFindBookingById(booking.getId(), user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateBookingDeniesNonOwnerCustomer() {
        var user = customer("00000000000");
        var booking = bookingWithOwnerCpf("12345678900");
        when(bookingPortIn.findBookingById(booking.getId())).thenReturn(booking);

        assertThatThrownBy(() -> controller.updateBooking(booking.getId(), new BookingUpdateDto(), null, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void updateBookingAllowsOwnerCustomer() {
        var user = customer("12345678900");
        var booking = bookingWithOwnerCpf("12345678900");
        when(bookingPortIn.findBookingById(booking.getId())).thenReturn(booking);
        when(bookingPortIn.updateBooking(org.mockito.ArgumentMatchers.eq(booking.getId()),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull())).thenReturn(booking);

        var response = controller.updateBooking(booking.getId(), new BookingUpdateDto(), null, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void cancelBookingDeniesNonOwnerCustomer() {
        var user = customer("00000000000");
        var booking = bookingWithOwnerCpf("12345678900");
        when(bookingPortIn.findBookingById(booking.getId())).thenReturn(booking);

        assertThatThrownBy(() -> controller.cancelBooking(booking.getId(), user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelBookingAllowsOwnerCustomer() {
        var user = customer("12345678900");
        var booking = bookingWithOwnerCpf("12345678900");
        when(bookingPortIn.findBookingById(booking.getId())).thenReturn(booking);
        when(bookingPortIn.cancelBooking(booking.getId())).thenReturn(booking);

        var response = controller.cancelBooking(booking.getId(), user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void confirmPaymentDelegatesToPortInWithoutOwnershipCheck() {
        var booking = bookingWithOwnerCpf("12345678900");
        var request = new com.petshop.booking_service.core.port.in.dto.PaymentConfirmationRequestDto();
        request.setPaymentMethod(com.petshop.booking_service.core.domain.enums.PaymentMethod.PIX);
        when(bookingPortIn.confirmPayment(booking.getId(), request)).thenReturn(booking);

        var response = controller.confirmPayment(booking.getId(), request, receptionist());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getId()).isEqualTo(booking.getId());
    }

    @Test
    void ownershipCheckDeniesCustomerWithNullCpf() {
        var user = new AuthenticatedUser(UUID.randomUUID(), "c@c.com", "C", null, "1", Role.CUSTOMER, AccountType.CUSTOMER);
        var booking = bookingWithOwnerCpf("12345678900");
        when(bookingPortIn.findBookingById(booking.getId())).thenReturn(booking);

        assertThatThrownBy(() -> controller.getFindBookingById(booking.getId(), user))
                .isInstanceOf(AccessDeniedException.class);
    }
}
