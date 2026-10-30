package com.petshop.order_service.infrastructure.persistence.postgresql.repository;

import com.petshop.order_service.core.domain.enums.OrderStatus;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.OrderEntity;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.OrderItemEmbeddable;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pagamento × expiração no Postgres de verdade: as duas transições
 * condicionais (AWAITING_PAYMENT → PAID / → CANCELED) disparadas juntas, em
 * transações separadas, e só uma pode vencer. H2 não serve aqui — o que se
 * testa é o lock de linha do UPDATE condicional do Postgres.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OrderStatusTransitionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14");

    @Autowired
    private OrderRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID awaitingOrder(LocalDateTime expiresAt) {
        var order = new OrderEntity();
        order.setId(UUID.randomUUID());
        order.setCustomerId(UUID.randomUUID());
        order.setItems(List.of(new OrderItemEmbeddable(1L, "Racao", 2, 10.0)));
        order.setTotalAmount(20.0);
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        order.setCreatedAt(LocalDateTime.now().minusMinutes(31));
        order.setExpiresAt(expiresAt);
        return repository.save(order).getId();
    }

    private int pay(UUID id) {
        var tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> repository.markAsPaidIfAwaitingPayment(id, LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(10), LocalDateTime.now(),
                OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID));
    }

    private int cancel(UUID id) {
        var tx = new TransactionTemplate(transactionManager);
        return tx.execute(status -> repository.cancelIfAwaitingPayment(id, OrderStatus.AWAITING_PAYMENT, OrderStatus.CANCELED));
    }

    private OrderStatus statusOf(UUID id) {
        return repository.findById(id).orElseThrow().getStatus();
    }

    @RepeatedTest(20)
    void paymentAndExpirationRacingOnTheSameOrderOnlyOneTransitionHappens() throws Exception {
        // Ainda dentro do prazo, então o pagamento também pode vencer.
        var id = awaitingOrder(LocalDateTime.now().plusMinutes(5));
        var start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Integer> paid = executor.submit(() -> { start.await(); return pay(id); });
            Future<Integer> canceled = executor.submit(() -> { start.await(); return cancel(id); });
            start.countDown();

            int paidRows = paid.get();
            int canceledRows = canceled.get();

            assertThat(paidRows + canceledRows).isEqualTo(1);
            assertThat(statusOf(id)).isEqualTo(paidRows == 1 ? OrderStatus.PAID : OrderStatus.CANCELED);
        }
    }

    @Test
    void secondTransitionAfterTheFirstAffectsNothing() {
        var id = awaitingOrder(LocalDateTime.now().plusMinutes(5));

        assertThat(cancel(id)).isEqualTo(1);
        assertThat(pay(id)).isZero();
        assertThat(cancel(id)).isZero();
        assertThat(statusOf(id)).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void paymentIsRefusedOnceTheReservationExpired() {
        var id = awaitingOrder(LocalDateTime.now().minusMinutes(1));

        assertThat(pay(id)).isZero();
        assertThat(statusOf(id)).isEqualTo(OrderStatus.AWAITING_PAYMENT);
    }

    @Test
    void findsOnlyAwaitingOrdersWhoseReservationExpired() {
        var expired = awaitingOrder(LocalDateTime.now().minusMinutes(1));
        var stillValid = awaitingOrder(LocalDateTime.now().plusMinutes(5));

        var found = repository.findByStatusAndExpiresAtBefore(OrderStatus.AWAITING_PAYMENT, LocalDateTime.now())
                .stream().map(OrderEntity::getId).toList();

        assertThat(found).contains(expired).doesNotContain(stillValid);
    }
}
