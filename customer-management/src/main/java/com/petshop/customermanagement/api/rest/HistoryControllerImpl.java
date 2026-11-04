package com.petshop.customermanagement.api.rest;

import com.petshop.customermanagement.api.rest.dto.BookingHistoryResponseDto;
import com.petshop.customermanagement.api.rest.dto.PurchaseHistoryResponseDto;
import com.petshop.customermanagement.core.port.in.HistoryPortIn;
import com.petshop.customermanagement.infrastructure.security.CustomerIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import com.petshop.commons.dto.PageResponse;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class HistoryControllerImpl implements HistoryController {

    private final HistoryPortIn historyPortIn;
    private final CustomerIdentityResolver customerIdentityResolver;

    @Override
    public ResponseEntity<List<BookingHistoryResponseDto>> getCustomerBookingHistory(UUID customerId, AuthenticatedUser user) {
        customerIdentityResolver.requireOwnershipIfCustomer(customerId, user);
        var history = historyPortIn.findCustomerBookingHistory(customerId)
                .stream()
                .map(BookingHistoryResponseDto::new)
                .toList();
        return ResponseEntity.ok(history);
    }

    @Override
    public ResponseEntity<List<PurchaseHistoryResponseDto>> getCustomerPurchaseHistory(UUID customerId, AuthenticatedUser user) {
        customerIdentityResolver.requireOwnershipIfCustomer(customerId, user);
        var history = historyPortIn.findCustomerPurchaseHistory(customerId)
                .stream()
                .map(PurchaseHistoryResponseDto::new)
                .toList();
        return ResponseEntity.ok(history);
    }

    @Override
    public ResponseEntity<PageResponse<BookingHistoryResponseDto>> getStoreBookingHistory(Pageable pageable) {
        var page = historyPortIn.findStoreBookingHistory(pageable).map(BookingHistoryResponseDto::new);
        return ResponseEntity.ok(new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast()));
    }

    @Override
    public ResponseEntity<PageResponse<PurchaseHistoryResponseDto>> getStorePurchaseHistory(Pageable pageable) {
        var page = historyPortIn.findStorePurchaseHistory(pageable).map(PurchaseHistoryResponseDto::new);
        return ResponseEntity.ok(new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast()));
    }
}
