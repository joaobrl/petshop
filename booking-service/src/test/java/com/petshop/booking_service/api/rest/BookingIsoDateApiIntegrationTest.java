package com.petshop.booking_service.api.rest;

import com.petshop.booking_service.core.domain.Booking;
import com.petshop.booking_service.core.domain.enums.RangeType;
import com.petshop.booking_service.core.domain.enums.ServiceType;
import com.petshop.booking_service.core.port.in.AvailableSlotsPortIn;
import com.petshop.booking_service.core.port.in.BookingPortIn;
import com.petshop.booking_service.core.port.in.dto.BookingRequestDto;
import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.booking_service.support.TestJwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingIsoDateApiIntegrationTest {

    private static final TestJwtService TEST_JWT_SERVICE =
            new TestJwtService();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingPortIn bookingPortIn;

    @MockitoBean
    private AvailableSlotsPortIn availableSlotsPortIn;

    private String bearer() {
        return "Bearer " + TEST_JWT_SERVICE.generateToken(UUID.randomUUID(), "user@petshop.com", "User",
                "12345678900", "11999990000", Role.CUSTOMER, AccountType.CUSTOMER);
    }

    private String createBody(String bookingDateTime) {
        return """
                {"petId":"%s","ownerName":"Ciclana","ownerCpf":"12345678900","ownerContact":"11999990000",
                 "ownerEmail":"ciclana@petshop.com","serviceType":"BANHO","bookingDateTime":"%s"}
                """.formatted(UUID.randomUUID(), bookingDateTime);
    }

    @Test
    @DisplayName("Deve aceitar bookingDateTime em ISO-8601 sem offset ao criar agendamento")
    void shouldAcceptCreateWhenBookingDateTimeIsIso() throws Exception {
        var requestDto = new BookingRequestDto();
        requestDto.setPetId(UUID.randomUUID().toString());
        requestDto.setServiceType(ServiceType.BANHO);
        requestDto.setBookingDateTime(LocalDateTime.of(2099, 8, 15, 9, 0));
        var booking = new Booking(requestDto);
        booking.setId(UUID.randomUUID());
        when(bookingPortIn.createBooking(any(), any(), any())).thenReturn(booking);

        mockMvc.perform(post("/api/v1/bookings/create")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("2099-08-15T09:00:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingDateTime").value("2099-08-15T09:00:00"));

        var captor = ArgumentCaptor.forClass(BookingRequestDto.class);
        verify(bookingPortIn).createBooking(captor.capture(), any(), any());
        assertThat(captor.getValue().getBookingDateTime()).isEqualTo(LocalDateTime.of(2099, 8, 15, 9, 0));
    }

    @Test
    @DisplayName("Deve rejeitar com 400 bookingDateTime no formato brasileiro ao criar agendamento")
    void shouldRejectCreateWhenBookingDateTimeIsBrazilianFormat() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/create")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("15/08/2099 09:00")))
                .andExpect(status().isBadRequest());

        verify(bookingPortIn, never()).createBooking(any(), any(), any());
    }

    @Test
    @DisplayName("Deve retornar 200 em available-slots quando date estiver em ISO-8601")
    void shouldReturnOkOnAvailableSlotsWhenDateIsIso() throws Exception {
        when(availableSlotsPortIn.findAvailableSlots(any(), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/bookings/available-slots")
                        .param("serviceType", "BANHO")
                        .param("date", "2026-08-10")
                        .param("range", "DAY"))
                .andExpect(status().isOk());

        verify(availableSlotsPortIn).findAvailableSlots(ServiceType.BANHO, LocalDate.of(2026, 8, 10), RangeType.DAY);
    }

    @Test
    @DisplayName("Deve retornar 400 em available-slots quando date estiver no formato brasileiro")
    void shouldReturnBadRequestOnAvailableSlotsWhenDateIsBrazilianFormat() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/available-slots")
                        .param("serviceType", "BANHO")
                        .param("date", "10/08/2026")
                        .param("range", "DAY"))
                .andExpect(status().isBadRequest());

        verify(availableSlotsPortIn, never()).findAvailableSlots(any(), any(), any());
    }

    @Test
    @DisplayName("Deve retornar 200 em list quando date estiver em ISO-8601")
    void shouldReturnOkOnListWhenDateIsIso() throws Exception {
        when(bookingPortIn.findBookings(any(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/api/v1/bookings/list")
                        .header("Authorization", bearer())
                        .param("date", "2026-08-10"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Deve retornar 400 em list quando date estiver no formato brasileiro")
    void shouldReturnBadRequestOnListWhenDateIsBrazilianFormat() throws Exception {
        mockMvc.perform(get("/api/v1/bookings/list")
                        .header("Authorization", bearer())
                        .param("date", "10/08/2026"))
                .andExpect(status().isBadRequest());

        verify(bookingPortIn, never()).findBookings(any(), any());
    }
}
