package com.petshop.customermanagement.api.rest;

import com.petshop.commons.dto.PageResponse;
import com.petshop.customermanagement.api.rest.dto.BookingHistoryResponseDto;
import com.petshop.customermanagement.api.rest.dto.PurchaseHistoryResponseDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

/**
 * Histórico gravado no Mongo a partir dos eventos Kafka (booking-completed/order-completed).
 * Endpoints com {customerId}: CUSTOMER só consulta o próprio (ownership via
 * CustomerIdentityResolver); ADMIN/RECEPTIONIST consultam qualquer um. Endpoints
 * sem customerId (histórico da loja inteira) são só ADMIN/RECEPTIONIST.
 */
@RequestMapping("/api/v1/history")
public interface HistoryController {

    @GetMapping("/bookings/{customerId}")
    ResponseEntity<List<BookingHistoryResponseDto>> getCustomerBookingHistory(@PathVariable UUID customerId, @RequestHeader("Authorization") String token);

    @GetMapping("/purchases/{customerId}")
    ResponseEntity<List<PurchaseHistoryResponseDto>> getCustomerPurchaseHistory(@PathVariable UUID customerId, @RequestHeader("Authorization") String token);

    @GetMapping("/bookings")
    ResponseEntity<PageResponse<BookingHistoryResponseDto>> getStoreBookingHistory(@PageableDefault(size = 20) Pageable pageable);

    @GetMapping("/purchases")
    ResponseEntity<PageResponse<PurchaseHistoryResponseDto>> getStorePurchaseHistory(@PageableDefault(size = 20) Pageable pageable);
}
