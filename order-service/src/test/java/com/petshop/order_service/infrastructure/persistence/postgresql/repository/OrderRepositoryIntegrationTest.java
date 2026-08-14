package com.petshop.order_service.infrastructure.persistence.postgresql.repository;

import com.petshop.order_service.core.domain.enums.OrderStatus;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.OrderEntity;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.OrderItemEmbeddable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class OrderRepositoryIntegrationTest {

    @Autowired
    private OrderRepository repository;

    private OrderEntity orderFor(UUID customerId) {
        var item = new OrderItemEmbeddable(1L, "Racao", 2, 10.0);
        var order = new OrderEntity();
        order.setId(UUID.randomUUID());
        order.setCustomerId(customerId);
        order.setItems(List.of(item));
        order.setTotalAmount(20.0);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    @Test
    void savesAndFindsOrderById() {
        var order = orderFor(UUID.randomUUID());

        var saved = repository.save(order);
        var found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getTotalAmount()).isEqualTo(20.0);
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByStatusAndCreatedAtBeforeReturnsOnlyOldEnoughAwaitingPaymentOrders() {
        var old = orderFor(UUID.randomUUID());
        old.setCreatedAt(LocalDateTime.now().minusMinutes(5));
        repository.save(old);

        var recent = orderFor(UUID.randomUUID());
        recent.setCreatedAt(LocalDateTime.now());
        repository.save(recent);

        var result = repository.findByStatusAndCreatedAtBefore(
                OrderStatus.AWAITING_PAYMENT, LocalDateTime.now().minusMinutes(1));

        assertThat(result).extracting(OrderEntity::getId).contains(old.getId()).doesNotContain(recent.getId());
    }

    @Test
    void findByStatusAndCreatedAtBeforeReturnsEmptyWhenNoneMatch() {
        var recent = orderFor(UUID.randomUUID());
        recent.setCreatedAt(LocalDateTime.now());
        repository.save(recent);

        var result = repository.findByStatusAndCreatedAtBefore(
                OrderStatus.AWAITING_PAYMENT, LocalDateTime.now().minusMinutes(1));

        assertThat(result).isEmpty();
    }

    @Test
    void findByStatusAndPickupReadyAtLessThanEqualReturnsOnlyDuePaidOrders() {
        var due = orderFor(UUID.randomUUID());
        due.setStatus(OrderStatus.PAID);
        due.setPaidAt(LocalDateTime.now());
        due.setPickupReadyAt(LocalDateTime.now().minusMinutes(5));
        repository.save(due);

        var notYetDue = orderFor(UUID.randomUUID());
        notYetDue.setStatus(OrderStatus.PAID);
        notYetDue.setPaidAt(LocalDateTime.now());
        notYetDue.setPickupReadyAt(LocalDateTime.now().plusMinutes(60));
        repository.save(notYetDue);

        var result = repository.findByStatusAndPickupReadyAtLessThanEqual(OrderStatus.PAID, LocalDateTime.now());

        assertThat(result).extracting(OrderEntity::getId).contains(due.getId()).doesNotContain(notYetDue.getId());
    }

    @Test
    void findByStatusAndPickupReadyAtLessThanEqualReturnsEmptyWhenNoneDue() {
        var notYetDue = orderFor(UUID.randomUUID());
        notYetDue.setStatus(OrderStatus.PAID);
        notYetDue.setPaidAt(LocalDateTime.now());
        notYetDue.setPickupReadyAt(LocalDateTime.now().plusMinutes(60));
        repository.save(notYetDue);

        assertThat(repository.findByStatusAndPickupReadyAtLessThanEqual(OrderStatus.PAID, LocalDateTime.now()))
                .isEmpty();
    }
}
