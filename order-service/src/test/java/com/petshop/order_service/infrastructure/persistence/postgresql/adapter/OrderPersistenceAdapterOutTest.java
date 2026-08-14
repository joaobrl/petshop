package com.petshop.order_service.infrastructure.persistence.postgresql.adapter;

import com.petshop.order_service.core.domain.Order;
import com.petshop.order_service.core.domain.enums.OrderStatus;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.OrderEntity;
import com.petshop.order_service.infrastructure.persistence.postgresql.mapper.OrderMapper;
import com.petshop.order_service.infrastructure.persistence.postgresql.repository.OrderRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPersistenceAdapterOutTest {

    @Mock
    private OrderRepository repository;

    @Mock
    private OrderMapper mapper;

    private OrderPersistenceAdapterOut adapter;

    @BeforeEach
    void setUp() {
        adapter = new OrderPersistenceAdapterOut(repository, mapper);
    }

    @Test
    void saveInsertsWhenOrderDoesNotYetExist() {
        var order = new Order();
        order.setId(UUID.randomUUID());
        var entity = new OrderEntity();
        entity.setId(order.getId());
        var savedEntity = new OrderEntity();
        savedEntity.setId(order.getId());
        var savedOrder = new Order();
        savedOrder.setId(order.getId());

        when(mapper.toEntity(order)).thenReturn(entity);
        when(repository.existsById(entity.getId())).thenReturn(false);
        when(repository.save(entity)).thenReturn(savedEntity);
        when(mapper.toDomain(savedEntity)).thenReturn(savedOrder);

        assertThat(adapter.save(order)).isEqualTo(savedOrder);
        assertThat(entity.isNew()).isTrue();
    }

    @Test
    void saveMarksEntityAsNotNewWhenOrderAlreadyExists() {
        var order = new Order();
        order.setId(UUID.randomUUID());
        var entity = new OrderEntity();
        entity.setId(order.getId());
        var savedOrder = new Order();
        savedOrder.setId(order.getId());

        when(mapper.toEntity(order)).thenReturn(entity);
        when(repository.existsById(entity.getId())).thenReturn(true);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDomain(entity)).thenReturn(savedOrder);

        assertThat(adapter.save(order)).isEqualTo(savedOrder);
        assertThat(entity.isNew()).isFalse();
    }

    @Test
    void findByIdDelegatesToRepository() {
        var id = UUID.randomUUID();
        var entity = new OrderEntity();
        entity.setId(id);
        var order = new Order();
        order.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(order);

        assertThat(adapter.findById(id)).contains(order);
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        var id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void findAwaitingPaymentCreatedBeforeDelegatesWithAwaitingPaymentStatus() {
        var entity = new OrderEntity();
        var order = new Order();
        var threshold = LocalDateTime.now();
        when(repository.findByStatusAndCreatedAtBefore(OrderStatus.AWAITING_PAYMENT, threshold))
                .thenReturn(List.of(entity));
        when(mapper.toDomainList(List.of(entity))).thenReturn(List.of(order));

        assertThat(adapter.findAwaitingPaymentCreatedBefore(threshold)).containsExactly(order);
    }

    @Test
    void findPaidAndPickupDueDelegatesWithPaidStatus() {
        var entity = new OrderEntity();
        var order = new Order();
        var now = LocalDateTime.now();
        when(repository.findByStatusAndPickupReadyAtLessThanEqual(OrderStatus.PAID, now))
                .thenReturn(List.of(entity));
        when(mapper.toDomainList(List.of(entity))).thenReturn(List.of(order));

        assertThat(adapter.findPaidAndPickupDue(now)).containsExactly(order);
    }
}
