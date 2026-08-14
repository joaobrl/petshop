package com.petshop.customermanagement.core.application.service;

import com.petshop.customermanagement.core.domain.BookingHistory;
import com.petshop.customermanagement.core.domain.PurchaseHistory;
import com.petshop.customermanagement.core.port.in.HistoryPortIn;
import com.petshop.customermanagement.core.port.out.BookingHistoryPortOut;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HistoryService implements HistoryPortIn {

    private final BookingHistoryPortOut bookingHistoryPortOut;
    private final PurchaseHistoryPortOut purchaseHistoryPortOut;

    @Override
    public List<BookingHistory> findCustomerBookingHistory(UUID customerId) {
        return bookingHistoryPortOut.findAllByCustomerId(customerId);
    }

    @Override
    public List<PurchaseHistory> findCustomerPurchaseHistory(UUID customerId) {
        return purchaseHistoryPortOut.findAllByCustomerId(customerId);
    }

    @Override
    public List<BookingHistory> findStoreBookingHistory() {
        return bookingHistoryPortOut.findAll();
    }

    @Override
    public List<PurchaseHistory> findStorePurchaseHistory() {
        return purchaseHistoryPortOut.findAll();
    }
}
