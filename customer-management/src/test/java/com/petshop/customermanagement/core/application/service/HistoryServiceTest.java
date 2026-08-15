package com.petshop.customermanagement.core.application.service;

import com.petshop.customermanagement.core.domain.BookingHistory;
import com.petshop.customermanagement.core.domain.PurchaseHistory;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistoryServiceTest {

    @Mock
    private BookingHistoryPortOut bookingHistoryPortOut;

    @Mock
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    private HistoryService historyService;

    @BeforeEach
    void setUp() {
        historyService = new HistoryService(bookingHistoryPortOut, purchaseHistoryPortOut);
    }

    @Test
    void findCustomerBookingHistoryDelegatesToPortOutWithCustomerId() {
        var customerId = UUID.randomUUID();
        var history = new BookingHistory();
        history.setCustomerId(customerId);
        when(bookingHistoryPortOut.findAllByCustomerId(customerId)).thenReturn(List.of(history));

        var result = historyService.findCustomerBookingHistory(customerId);

        assertThat(result).containsExactly(history);
    }

    @Test
    void findCustomerBookingHistoryReturnsEmptyWhenNoneFound() {
        var customerId = UUID.randomUUID();
        when(bookingHistoryPortOut.findAllByCustomerId(customerId)).thenReturn(List.of());

        assertThat(historyService.findCustomerBookingHistory(customerId)).isEmpty();
    }

    @Test
    void findCustomerPurchaseHistoryDelegatesToPortOutWithCustomerId() {
        var customerId = UUID.randomUUID();
        var history = new PurchaseHistory();
        history.setCustomerId(customerId);
        when(purchaseHistoryPortOut.findAllByCustomerId(customerId)).thenReturn(List.of(history));

        var result = historyService.findCustomerPurchaseHistory(customerId);

        assertThat(result).containsExactly(history);
    }

    @Test
    void findCustomerPurchaseHistoryReturnsEmptyWhenNoneFound() {
        var customerId = UUID.randomUUID();
        when(purchaseHistoryPortOut.findAllByCustomerId(customerId)).thenReturn(List.of());

        assertThat(historyService.findCustomerPurchaseHistory(customerId)).isEmpty();
    }

    private final PageRequest pageable = PageRequest.of(0, 20);

    @Test
    void findStoreBookingHistoryDelegatesToFindAll() {
        var history = new BookingHistory();
        when(bookingHistoryPortOut.findAll(pageable)).thenReturn(new PageImpl<>(List.of(history)));

        assertThat(historyService.findStoreBookingHistory(pageable).getContent()).containsExactly(history);
    }

    @Test
    void findStoreBookingHistoryReturnsEmptyWhenNoBookingsAnywhere() {
        when(bookingHistoryPortOut.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        assertThat(historyService.findStoreBookingHistory(pageable).getContent()).isEmpty();
    }

    @Test
    void findStorePurchaseHistoryDelegatesToFindAll() {
        var history = new PurchaseHistory();
        when(purchaseHistoryPortOut.findAll(pageable)).thenReturn(new PageImpl<>(List.of(history)));

        assertThat(historyService.findStorePurchaseHistory(pageable).getContent()).containsExactly(history);
    }

    @Test
    void findStorePurchaseHistoryReturnsEmptyWhenNoPurchasesAnywhere() {
        when(purchaseHistoryPortOut.findAll(pageable)).thenReturn(new PageImpl<>(List.of()));

        assertThat(historyService.findStorePurchaseHistory(pageable).getContent()).isEmpty();
    }
}
