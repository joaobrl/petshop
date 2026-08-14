package com.petshop.order_service.infrastructure.persistence.postgresql.adapter;

import com.petshop.order_service.core.domain.Cart;
import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.CartEntity;
import com.petshop.order_service.infrastructure.persistence.postgresql.mapper.CartMapper;
import com.petshop.order_service.infrastructure.persistence.postgresql.repository.CartRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartPersistenceAdapterOutTest {

    @Mock
    private CartRepository repository;

    @Mock
    private CartMapper mapper;

    private CartPersistenceAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new CartPersistenceAdapterOut(repository, mapper);
    }

    @Test
    void saveInsertsWhenCartDoesNotYetExist() {
        var cart = Cart.openFor(UUID.randomUUID(), 24);
        var entity = new CartEntity();
        entity.setId(cart.getId());
        var savedCart = new Cart();
        savedCart.setId(cart.getId());

        when(mapper.toEntity(cart)).thenReturn(entity);
        when(repository.existsById(entity.getId())).thenReturn(false);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(savedCart);

        assertThat(adapter.save(cart)).isEqualTo(savedCart);
        assertThat(entity.isNew()).isTrue();
    }

    @Test
    void saveMarksEntityAsNotNewWhenCartAlreadyExists() {
        var cart = Cart.openFor(UUID.randomUUID(), 24);
        var entity = new CartEntity();
        entity.setId(cart.getId());
        var savedCart = new Cart();
        savedCart.setId(cart.getId());

        when(mapper.toEntity(cart)).thenReturn(entity);
        when(repository.existsById(entity.getId())).thenReturn(true);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(savedCart);

        assertThat(adapter.save(cart)).isEqualTo(savedCart);
        assertThat(entity.isNew()).isFalse();
    }

    @Test
    void findOpenCartByCustomerIdDelegatesWithOpenStatus() {
        var customerId = UUID.randomUUID();
        var cart = Cart.openFor(customerId, 24);
        var entity = new CartEntity();
        entity.setId(cart.getId());
        when(repository.findByCustomerIdAndStatus(customerId, CartStatus.OPEN)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(cart);

        assertThat(adapter.findOpenCartByCustomerId(customerId)).contains(cart);
    }

    @Test
    void findByIdDelegatesToRepository() {
        var id = UUID.randomUUID();
        var cart = Cart.openFor(UUID.randomUUID(), 24);
        var entity = new CartEntity();
        entity.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(cart);

        assertThat(adapter.findById(id)).contains(cart);
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void findExpiredOpenCartsDelegatesWithOpenStatusAndNow() {
        var cart = Cart.openFor(UUID.randomUUID(), 24);
        var entity = new CartEntity();
        entity.setId(cart.getId());
        when(repository.findByStatusAndExpiresAtBefore(eq(CartStatus.OPEN), any(LocalDateTime.class)))
                .thenReturn(List.of(entity));
        when(mapper.toDomainList(List.of(entity))).thenReturn(List.of(cart));

        assertThat(adapter.findExpiredOpenCarts()).containsExactly(cart);
    }
}
