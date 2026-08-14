package com.petshop.order_service.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.port.in.CartPortIn;
import com.petshop.order_service.core.port.in.dto.AddCartItemRequestDto;
import com.petshop.order_service.infrastructure.security.CustomerIdentityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartControllerImplTest {

    @Mock
    private CartPortIn cartPortIn;
    @Mock
    private CustomerIdentityResolver customerIdentityResolver;

    private CartControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new CartControllerImpl(cartPortIn, customerIdentityResolver);
    }

    private AuthenticatedUser customerUser(UUID id) {
        return new AuthenticatedUser(id, "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER);
    }

    @Test
    void addItemReservesStockWhenUserIsAuthenticated() {
        var customerId = UUID.randomUUID();
        var user = customerUser(customerId);
        var request = new AddCartItemRequestDto();
        request.setCustomerId(UUID.randomUUID());
        request.setProductId(1L);
        request.setQuantity(2);
        var cart = Cart.openFor(customerId, 24);
        when(customerIdentityResolver.resolveCustomerId(request.getCustomerId(), user)).thenReturn(customerId);
        when(cartPortIn.addItem(customerId, 1L, 2, true)).thenReturn(cart);

        var response = controller.addItem(request, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(cart.getId());
    }

    @Test
    void addItemDoesNotReserveStockWhenUserIsAnonymous() {
        var customerId = UUID.randomUUID();
        var request = new AddCartItemRequestDto();
        request.setCustomerId(customerId);
        request.setProductId(1L);
        request.setQuantity(2);
        var cart = Cart.openFor(customerId, 24);
        when(customerIdentityResolver.resolveCustomerId(customerId, null)).thenReturn(customerId);
        when(cartPortIn.addItem(customerId, 1L, 2, false)).thenReturn(cart);

        var response = controller.addItem(request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void removeItemDelegatesToResolvedCustomerId() {
        var customerId = UUID.randomUUID();
        var user = customerUser(customerId);
        var cart = Cart.openFor(customerId, 24);
        when(customerIdentityResolver.resolveCustomerId(customerId, user)).thenReturn(customerId);
        when(cartPortIn.removeItem(customerId, 1L)).thenReturn(cart);

        var response = controller.removeItem(1L, customerId, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(cart.getId());
    }

    @Test
    void getCartDelegatesToResolvedCustomerId() {
        var customerId = UUID.randomUUID();
        var cart = Cart.openFor(customerId, 24);
        when(customerIdentityResolver.resolveCustomerId(customerId, null)).thenReturn(customerId);
        when(cartPortIn.getCart(customerId)).thenReturn(cart);

        var response = controller.getCart(customerId, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(cart.getId());
    }
}
