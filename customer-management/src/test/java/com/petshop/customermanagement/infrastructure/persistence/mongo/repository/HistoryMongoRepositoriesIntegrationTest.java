package com.petshop.customermanagement.infrastructure.persistence.mongo.repository;

import com.petshop.customermanagement.core.domain.PurchaseHistoryItem;
import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerHistoryBookings;
import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerPurchaseHistory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa os repositórios Mongo contra um MongoDB real via Testcontainers —
 * precisa de Docker rodando (mesmo requisito de
 * OrderHistoryKafkaListenerIntegrationTest).
 */
@DataMongoTest
@Testcontainers
class HistoryMongoRepositoriesIntegrationTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:6.0");

    @Nested
    class BookingRepository {

        @Autowired
        private CustomerHistoryBookingsMongoRepository repository;

        @Test
        void savesAndFindsByBookingId() {
            var bookingId = UUID.randomUUID();
            var entity = new CustomerHistoryBookings();
            entity.setBookingId(bookingId);
            entity.setCustomerId(UUID.randomUUID());
            entity.setServiceType("Banho");
            entity.setBookingDate("10/08/2026");
            entity.setBookingTime("14:00");
            entity.setStatus("CONFIRMED");
            repository.save(entity);

            var found = repository.findByBookingId(bookingId);

            assertThat(found).isPresent();
            assertThat(found.get().getServiceType()).isEqualTo("Banho");
        }

        @Test
        void findByBookingIdReturnsEmptyWhenNotFound() {
            assertThat(repository.findByBookingId(UUID.randomUUID())).isEmpty();
        }

        @Test
        void findAllByCustomerIdReturnsOnlyThatCustomersBookings() {
            var customerId = UUID.randomUUID();
            var mine = new CustomerHistoryBookings();
            mine.setBookingId(UUID.randomUUID());
            mine.setCustomerId(customerId);
            var someoneElses = new CustomerHistoryBookings();
            someoneElses.setBookingId(UUID.randomUUID());
            someoneElses.setCustomerId(UUID.randomUUID());
            repository.save(mine);
            repository.save(someoneElses);

            var result = repository.findAllByCustomerId(customerId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCustomerId()).isEqualTo(customerId);
        }

        @Test
        void findAllReturnsEveryBookingAcrossCustomers() {
            repository.deleteAll();
            repository.save(bookingFor(UUID.randomUUID()));
            repository.save(bookingFor(UUID.randomUUID()));

            assertThat(repository.findAll()).hasSize(2);
        }

        private CustomerHistoryBookings bookingFor(UUID customerId) {
            var entity = new CustomerHistoryBookings();
            entity.setBookingId(UUID.randomUUID());
            entity.setCustomerId(customerId);
            return entity;
        }
    }

    @Nested
    class PurchaseRepository {

        @Autowired
        private CustomerPurchaseHistoryMongoRepository repository;

        @Test
        void savesAndFindsByOrderId() {
            var orderId = UUID.randomUUID();
            var entity = new CustomerPurchaseHistory();
            entity.setOrderId(orderId);
            entity.setCustomerId(UUID.randomUUID());
            entity.setItems(List.of(new PurchaseHistoryItem(1L, "Racao", 2, 50.0)));
            entity.setTotalAmount(100.0);
            entity.setPaidAt(LocalDateTime.now());
            repository.save(entity);

            var found = repository.findByOrderId(orderId);

            assertThat(found).isPresent();
            assertThat(found.get().getTotalAmount()).isEqualTo(100.0);
        }

        @Test
        void findByOrderIdReturnsEmptyWhenNotFound() {
            assertThat(repository.findByOrderId(UUID.randomUUID())).isEmpty();
        }

        @Test
        void findAllByCustomerIdReturnsOnlyThatCustomersPurchases() {
            var customerId = UUID.randomUUID();
            var mine = new CustomerPurchaseHistory();
            mine.setOrderId(UUID.randomUUID());
            mine.setCustomerId(customerId);
            var someoneElses = new CustomerPurchaseHistory();
            someoneElses.setOrderId(UUID.randomUUID());
            someoneElses.setCustomerId(UUID.randomUUID());
            repository.save(mine);
            repository.save(someoneElses);

            var result = repository.findAllByCustomerId(customerId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCustomerId()).isEqualTo(customerId);
        }

        @Test
        void findAllReturnsEveryPurchaseAcrossCustomers() {
            repository.deleteAll();
            repository.save(purchaseFor(UUID.randomUUID()));
            repository.save(purchaseFor(UUID.randomUUID()));

            assertThat(repository.findAll()).hasSize(2);
        }

        private CustomerPurchaseHistory purchaseFor(UUID customerId) {
            var entity = new CustomerPurchaseHistory();
            entity.setOrderId(UUID.randomUUID());
            entity.setCustomerId(customerId);
            return entity;
        }
    }
}
