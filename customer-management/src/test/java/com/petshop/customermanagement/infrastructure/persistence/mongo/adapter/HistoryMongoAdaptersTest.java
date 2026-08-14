package com.petshop.customermanagement.infrastructure.persistence.mongo.adapter;

import com.petshop.customermanagement.core.domain.BookingHistory;
import com.petshop.customermanagement.core.domain.PurchaseHistory;
import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerHistoryBookings;
import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerPurchaseHistory;
import com.petshop.customermanagement.infrastructure.persistence.mongo.mapper.BookingHistoryMapper;
import com.petshop.customermanagement.infrastructure.persistence.mongo.mapper.PurchaseHistoryMapper;
import com.petshop.customermanagement.infrastructure.persistence.mongo.repository.CustomerHistoryBookingsMongoRepository;
import com.petshop.customermanagement.infrastructure.persistence.mongo.repository.CustomerPurchaseHistoryMongoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HistoryMongoAdaptersTest {

    @Nested
    class BookingHistoryAdapterOutTest {

        @Mock
        private CustomerHistoryBookingsMongoRepository repository;

        @Mock
        private BookingHistoryMapper mapper;

        private BookingHistoryAdapterOut adapter;

        @BeforeEach
        void setUp() {
            adapter = new BookingHistoryAdapterOut(repository, mapper);
        }

        @Test
        void findByBookingIdReturnsMappedHistoryWhenPresent() {
            var bookingId = UUID.randomUUID();
            var entity = new CustomerHistoryBookings();
            var domain = new BookingHistory();
            when(repository.findByBookingId(bookingId)).thenReturn(Optional.of(entity));
            when(mapper.toDomain(entity)).thenReturn(domain);

            assertThat(adapter.findByBookingId(bookingId)).contains(domain);
        }

        @Test
        void findByBookingIdReturnsEmptyWhenAbsent() {
            var bookingId = UUID.randomUUID();
            when(repository.findByBookingId(bookingId)).thenReturn(Optional.empty());

            assertThat(adapter.findByBookingId(bookingId)).isEmpty();
        }

        @Test
        void findAllByCustomerIdMapsEveryEntity() {
            var customerId = UUID.randomUUID();
            var entity = new CustomerHistoryBookings();
            var domain = new BookingHistory();
            when(repository.findAllByCustomerId(customerId)).thenReturn(List.of(entity));
            when(mapper.toDomain(entity)).thenReturn(domain);

            assertThat(adapter.findAllByCustomerId(customerId)).containsExactly(domain);
        }

        @Test
        void findAllByCustomerIdReturnsEmptyWhenNoneFound() {
            var customerId = UUID.randomUUID();
            when(repository.findAllByCustomerId(customerId)).thenReturn(List.of());

            assertThat(adapter.findAllByCustomerId(customerId)).isEmpty();
        }

        @Test
        void findAllMapsEveryEntityAcrossAllCustomers() {
            var entity = new CustomerHistoryBookings();
            var domain = new BookingHistory();
            when(repository.findAll()).thenReturn(List.of(entity));
            when(mapper.toDomain(entity)).thenReturn(domain);

            assertThat(adapter.findAll()).containsExactly(domain);
        }

        @Test
        void saveKeepsExistingMongoIdWhenBookingAlreadyRecorded() {
            var bookingId = UUID.randomUUID();
            var domain = new BookingHistory();
            domain.setBookingId(bookingId);

            var newEntity = new CustomerHistoryBookings();
            var existingEntity = new CustomerHistoryBookings();
            existingEntity.setId("existing-mongo-id");
            var savedEntity = new CustomerHistoryBookings();
            var savedDomain = new BookingHistory();

            when(mapper.toEntity(domain)).thenReturn(newEntity);
            when(repository.findByBookingId(bookingId)).thenReturn(Optional.of(existingEntity));
            when(repository.save(newEntity)).thenReturn(savedEntity);
            when(mapper.toDomain(savedEntity)).thenReturn(savedDomain);

            var result = adapter.save(domain);

            assertThat(newEntity.getId()).isEqualTo("existing-mongo-id");
            assertThat(result).isEqualTo(savedDomain);
        }

        @Test
        void saveLeavesIdUnsetWhenBookingIsNew() {
            var bookingId = UUID.randomUUID();
            var domain = new BookingHistory();
            domain.setBookingId(bookingId);

            var newEntity = new CustomerHistoryBookings();
            when(mapper.toEntity(domain)).thenReturn(newEntity);
            when(repository.findByBookingId(bookingId)).thenReturn(Optional.empty());
            when(repository.save(newEntity)).thenReturn(newEntity);
            when(mapper.toDomain(newEntity)).thenReturn(domain);

            adapter.save(domain);

            assertThat(newEntity.getId()).isNull();
        }
    }

    @Nested
    class PurchaseHistoryAdapterOutTest {

        @Mock
        private CustomerPurchaseHistoryMongoRepository repository;

        @Mock
        private PurchaseHistoryMapper mapper;

        private PurchaseHistoryAdapterOut adapter;

        @BeforeEach
        void setUp() {
            adapter = new PurchaseHistoryAdapterOut(repository, mapper);
        }

        @Test
        void findByOrderIdReturnsMappedHistoryWhenPresent() {
            var orderId = UUID.randomUUID();
            var entity = new CustomerPurchaseHistory();
            var domain = new PurchaseHistory();
            when(repository.findByOrderId(orderId)).thenReturn(Optional.of(entity));
            when(mapper.toDomain(entity)).thenReturn(domain);

            assertThat(adapter.findByOrderId(orderId)).contains(domain);
        }

        @Test
        void findByOrderIdReturnsEmptyWhenAbsent() {
            var orderId = UUID.randomUUID();
            when(repository.findByOrderId(orderId)).thenReturn(Optional.empty());

            assertThat(adapter.findByOrderId(orderId)).isEmpty();
        }

        @Test
        void findAllByCustomerIdMapsEveryEntity() {
            var customerId = UUID.randomUUID();
            var entity = new CustomerPurchaseHistory();
            var domain = new PurchaseHistory();
            when(repository.findAllByCustomerId(customerId)).thenReturn(List.of(entity));
            when(mapper.toDomain(entity)).thenReturn(domain);

            assertThat(adapter.findAllByCustomerId(customerId)).containsExactly(domain);
        }

        @Test
        void findAllByCustomerIdReturnsEmptyWhenNoneFound() {
            var customerId = UUID.randomUUID();
            when(repository.findAllByCustomerId(customerId)).thenReturn(List.of());

            assertThat(adapter.findAllByCustomerId(customerId)).isEmpty();
        }

        @Test
        void findAllMapsEveryEntityAcrossAllCustomers() {
            var entity = new CustomerPurchaseHistory();
            var domain = new PurchaseHistory();
            when(repository.findAll()).thenReturn(List.of(entity));
            when(mapper.toDomain(entity)).thenReturn(domain);

            assertThat(adapter.findAll()).containsExactly(domain);
        }

        @Test
        void saveKeepsExistingMongoIdWhenOrderAlreadyRecorded() {
            var orderId = UUID.randomUUID();
            var domain = new PurchaseHistory();
            domain.setOrderId(orderId);

            var newEntity = new CustomerPurchaseHistory();
            var existingEntity = new CustomerPurchaseHistory();
            existingEntity.setId("existing-mongo-id");
            var savedEntity = new CustomerPurchaseHistory();
            var savedDomain = new PurchaseHistory();

            when(mapper.toEntity(domain)).thenReturn(newEntity);
            when(repository.findByOrderId(orderId)).thenReturn(Optional.of(existingEntity));
            when(repository.save(newEntity)).thenReturn(savedEntity);
            when(mapper.toDomain(savedEntity)).thenReturn(savedDomain);

            var result = adapter.save(domain);

            assertThat(newEntity.getId()).isEqualTo("existing-mongo-id");
            assertThat(result).isEqualTo(savedDomain);
        }

        @Test
        void saveLeavesIdUnsetWhenOrderIsNew() {
            var orderId = UUID.randomUUID();
            var domain = new PurchaseHistory();
            domain.setOrderId(orderId);

            var newEntity = new CustomerPurchaseHistory();
            when(mapper.toEntity(domain)).thenReturn(newEntity);
            when(repository.findByOrderId(orderId)).thenReturn(Optional.empty());
            when(repository.save(newEntity)).thenReturn(newEntity);
            when(mapper.toDomain(newEntity)).thenReturn(domain);

            adapter.save(domain);

            assertThat(newEntity.getId()).isNull();
        }
    }
}
