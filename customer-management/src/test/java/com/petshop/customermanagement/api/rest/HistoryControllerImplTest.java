package com.petshop.customermanagement.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.customermanagement.core.domain.BookingHistory;
import com.petshop.customermanagement.core.domain.PurchaseHistory;
import com.petshop.customermanagement.core.port.in.HistoryPortIn;
import com.petshop.customermanagement.infrastructure.security.CustomerIdentityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryControllerImplTest {

    @Mock
    private HistoryPortIn historyPortIn;

    @Mock
    private CustomerIdentityResolver customerIdentityResolver;

    private HistoryControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new HistoryControllerImpl(historyPortIn, customerIdentityResolver);
    }

    private AuthenticatedUser user() {
        return new AuthenticatedUser(UUID.randomUUID(), "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER);
    }

    @Test
    void getCustomerBookingHistoryChecksOwnershipBeforeDelegating() {
        var customerId = UUID.randomUUID();
        var user = user();
        var history = new BookingHistory();
        history.setCustomerId(customerId);
        when(historyPortIn.findCustomerBookingHistory(customerId)).thenReturn(List.of(history));

        var response = controller.getCustomerBookingHistory(customerId, user);

        InOrder inOrder = inOrder(customerIdentityResolver, historyPortIn);
        inOrder.verify(customerIdentityResolver).requireOwnershipIfCustomer(customerId, user);
        inOrder.verify(historyPortIn).findCustomerBookingHistory(customerId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getCustomerBookingHistoryPropagatesAccessDeniedWithoutCallingPortIn() {
        var customerId = UUID.randomUUID();
        var user = user();
        doThrow(new AccessDeniedException("nope")).when(customerIdentityResolver)
                .requireOwnershipIfCustomer(customerId, user);

        assertThatThrownBy(() -> controller.getCustomerBookingHistory(customerId, user))
                .isInstanceOf(AccessDeniedException.class);

        verify(historyPortIn, never()).findCustomerBookingHistory(any());
    }

    @Test
    void getCustomerBookingHistoryReturnsEmptyListWhenNoHistory() {
        var customerId = UUID.randomUUID();
        var user = user();
        when(historyPortIn.findCustomerBookingHistory(customerId)).thenReturn(List.of());

        var response = controller.getCustomerBookingHistory(customerId, user);

        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void getCustomerPurchaseHistoryChecksOwnershipBeforeDelegating() {
        var customerId = UUID.randomUUID();
        var user = user();
        var history = new PurchaseHistory();
        history.setCustomerId(customerId);
        when(historyPortIn.findCustomerPurchaseHistory(customerId)).thenReturn(List.of(history));

        var response = controller.getCustomerPurchaseHistory(customerId, user);

        InOrder inOrder = inOrder(customerIdentityResolver, historyPortIn);
        inOrder.verify(customerIdentityResolver).requireOwnershipIfCustomer(customerId, user);
        inOrder.verify(historyPortIn).findCustomerPurchaseHistory(customerId);
        assertThat(response.getBody()).hasSize(1);
    }

    @Test
    void getCustomerPurchaseHistoryPropagatesAccessDeniedWithoutCallingPortIn() {
        var customerId = UUID.randomUUID();
        var user = user();
        doThrow(new AccessDeniedException("nope")).when(customerIdentityResolver)
                .requireOwnershipIfCustomer(customerId, user);

        assertThatThrownBy(() -> controller.getCustomerPurchaseHistory(customerId, user))
                .isInstanceOf(AccessDeniedException.class);

        verify(historyPortIn, never()).findCustomerPurchaseHistory(any());
    }

    @Test
    void getStoreBookingHistoryDoesNotCheckOwnershipAndDelegatesDirectly() {
        when(historyPortIn.findStoreBookingHistory()).thenReturn(List.of(new BookingHistory(), new BookingHistory()));

        var response = controller.getStoreBookingHistory();

        assertThat(response.getBody()).hasSize(2);
        verifyNoInteractions(customerIdentityResolver);
    }

    @Test
    void getStoreBookingHistoryReturnsEmptyWhenNoBookingsAnywhere() {
        when(historyPortIn.findStoreBookingHistory()).thenReturn(List.of());

        assertThat(controller.getStoreBookingHistory().getBody()).isEmpty();
    }

    @Test
    void getStorePurchaseHistoryDoesNotCheckOwnershipAndDelegatesDirectly() {
        when(historyPortIn.findStorePurchaseHistory()).thenReturn(List.of(new PurchaseHistory()));

        var response = controller.getStorePurchaseHistory();

        assertThat(response.getBody()).hasSize(1);
        verifyNoInteractions(customerIdentityResolver);
    }

    @Test
    void getStorePurchaseHistoryReturnsEmptyWhenNoPurchasesAnywhere() {
        when(historyPortIn.findStorePurchaseHistory()).thenReturn(List.of());

        assertThat(controller.getStorePurchaseHistory().getBody()).isEmpty();
    }
}
