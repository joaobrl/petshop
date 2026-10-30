package com.petshop.order_service.api.rest;

import com.petshop.commons.security.Role;
import com.petshop.commons.security.jwt.AccountType;
import com.petshop.commons.security.jwt.AuthenticatedUser;
import com.petshop.commons.security.jwt.JwtService;
import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.core.port.in.OrderPortIn;
import com.petshop.order_service.core.port.in.dto.CheckoutRequestDto;
import com.petshop.order_service.infrastructure.security.CustomerIdentityResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderControllerImplTest {

    private static final JwtService JWT_SERVICE =
            new JwtService("test-secret-key-for-jwt-signing-min-32-bytes-long", Duration.ofMinutes(60));

    @Mock
    private OrderPortIn orderPortIn;
    @Mock
    private CustomerIdentityResolver customerIdentityResolver;

    private OrderControllerImpl controller;

    @BeforeEach
    void setUp() {
        controller = new OrderControllerImpl(orderPortIn, customerIdentityResolver, JWT_SERVICE);
    }

    private AuthenticatedUser customerUser(UUID id) {
        return new AuthenticatedUser(id, "maria@mail.com", "Maria", "12345678900", "11999999999", Role.CUSTOMER, AccountType.CUSTOMER);
    }

    private String bearerTokenFor(AuthenticatedUser user) {
        return "Bearer " + JWT_SERVICE.generateToken(user.id(), user.email(), user.name(), user.cpf(), user.phone(), user.role(), user.type());
    }

    private Order sampleOrder(UUID customerId) {
        var cart = Cart.openFor(customerId, 24);
        cart.addOrIncrementItem(1L, "Racao", 10.0, 2);
        var order = Order.fromCartItems(cart.getCustomerId(), cart.getItems(), 30);
        order.setId(UUID.randomUUID());
        return order;
    }

    @Test
    void checkoutReturnsCreatedWithLocationHeader() {
        var customerId = UUID.randomUUID();
        var user = customerUser(customerId);
        var token = bearerTokenFor(user);
        var order = sampleOrder(customerId);
        when(customerIdentityResolver.resolveCustomerId(customerId, user)).thenReturn(customerId);
        when(orderPortIn.checkout(customerId, List.of(1L), token)).thenReturn(order);

        var response = controller.checkout(customerId, new CheckoutRequestDto(List.of(1L)), UriComponentsBuilder.fromUriString("http://localhost:8086"), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("http://localhost:8086/api/v1/orders/" + order.getId());
        assertThat(response.getBody().getId()).isEqualTo(order.getId());
    }

    @Test
    void getOrderByIdChecksOwnershipAndReturnsMappedOrder() {
        var customerId = UUID.randomUUID();
        var user = customerUser(customerId);
        var token = bearerTokenFor(user);
        var order = sampleOrder(customerId);
        when(orderPortIn.findById(order.getId())).thenReturn(order);

        var response = controller.getOrderById(order.getId(), token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getId()).isEqualTo(order.getId());
        verify(customerIdentityResolver).requireOwnershipIfCustomer(order.getCustomerId(), user);
    }
}
