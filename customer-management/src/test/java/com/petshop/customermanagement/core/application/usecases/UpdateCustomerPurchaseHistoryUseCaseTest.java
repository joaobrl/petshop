package com.petshop.customermanagement.core.application.usecases;

import com.petshop.customermanagement.core.domain.PurchaseHistory;
import com.petshop.customermanagement.core.port.in.dto.PurchaseCompletedCommand;
import com.petshop.customermanagement.core.port.out.PurchaseHistoryPortOut;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateCustomerPurchaseHistoryUseCaseTest {

    @Mock
    private PurchaseHistoryPortOut purchaseHistoryPortOut;

    private UpdateCustomerPurchaseHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateCustomerPurchaseHistoryUseCase(purchaseHistoryPortOut);
    }

    private PurchaseCompletedCommand event(UUID orderId, UUID customerId) {
        return new PurchaseCompletedCommand(
                orderId,
                customerId,
                List.of(new PurchaseCompletedCommand.PurchaseItemCommand(1L, "Racao Premium", 2, 79.90)),
                159.80,
                LocalDateTime.of(2026, 8, 5, 10, 0)
        );
    }

    @Test
    void createsNewHistoryWhenOrderNotYetRecorded() {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var event = event(orderId, customerId);

        when(purchaseHistoryPortOut.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(purchaseHistoryPortOut.save(any(PurchaseHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(event);

        ArgumentCaptor<PurchaseHistory> captor = ArgumentCaptor.forClass(PurchaseHistory.class);
        verify(purchaseHistoryPortOut).save(captor.capture());
        var saved = captor.getValue();

        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getCustomerId()).isEqualTo(customerId);
        assertThat(saved.getTotalAmount()).isEqualTo(159.80);
        assertThat(saved.getPaidAt()).isEqualTo(LocalDateTime.of(2026, 8, 5, 10, 0));
        assertThat(saved.getItems()).hasSize(1);
        assertThat(saved.getItems().get(0).getProductName()).isEqualTo("Racao Premium");
        assertThat(saved.getItems().get(0).getProductId()).isEqualTo(1L);
        assertThat(saved.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(saved.getItems().get(0).getUnitPrice()).isEqualTo(79.90);
    }

    @Test
    void updatesExistingHistoryWhenOrderAlreadyRecorded() {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var event = event(orderId, customerId);

        var existing = new PurchaseHistory();
        existing.setOrderId(orderId);
        existing.setCustomerId(customerId);

        when(purchaseHistoryPortOut.findByOrderId(orderId)).thenReturn(Optional.of(existing));
        when(purchaseHistoryPortOut.save(any(PurchaseHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(event);

        ArgumentCaptor<PurchaseHistory> captor = ArgumentCaptor.forClass(PurchaseHistory.class);
        verify(purchaseHistoryPortOut).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getTotalAmount()).isEqualTo(159.80);
    }

    @Test
    void mapsMultipleItemsCorrectly() {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var event = new PurchaseCompletedCommand(
                orderId,
                customerId,
                List.of(
                        new PurchaseCompletedCommand.PurchaseItemCommand(1L, "Racao", 1, 50.0),
                        new PurchaseCompletedCommand.PurchaseItemCommand(2L, "Brinquedo", 3, 10.0)
                ),
                80.0,
                LocalDateTime.now()
        );
        when(purchaseHistoryPortOut.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(purchaseHistoryPortOut.save(any(PurchaseHistory.class))).thenAnswer(inv -> inv.getArgument(0));

        useCase.execute(event);

        ArgumentCaptor<PurchaseHistory> captor = ArgumentCaptor.forClass(PurchaseHistory.class);
        verify(purchaseHistoryPortOut).save(captor.capture());
        assertThat(captor.getValue().getItems()).hasSize(2);
    }
}
