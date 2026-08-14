package com.petshop.customermanagement.infrastructure.persistence.mongo.mapper;

import com.petshop.customermanagement.core.domain.BookingHistory;
import com.petshop.customermanagement.core.domain.PurchaseHistory;
import com.petshop.customermanagement.core.domain.PurchaseHistoryItem;
import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerHistoryBookings;
import com.petshop.customermanagement.infrastructure.persistence.mongo.entity.CustomerPurchaseHistory;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryMongoMappersTest {

    @Nested
    class BookingHistoryMapperTest {

        private final BookingHistoryMapper mapper = Mappers.getMapper(BookingHistoryMapper.class);

        @Test
        void toDomainMapsAllFields() {
            var entity = new CustomerHistoryBookings();
            entity.setId("mongo-id-1");
            entity.setBookingId(UUID.randomUUID());
            entity.setCustomerId(UUID.randomUUID());
            entity.setServiceType("Banho");
            entity.setBookingDate("10/08/2026");
            entity.setBookingTime("14:00");
            entity.setStatus("CONFIRMED");

            var domain = mapper.toDomain(entity);

            assertThat(domain.getBookingId()).isEqualTo(entity.getBookingId());
            assertThat(domain.getCustomerId()).isEqualTo(entity.getCustomerId());
            assertThat(domain.getServiceType()).isEqualTo("Banho");
            assertThat(domain.getBookingDate()).isEqualTo("10/08/2026");
            assertThat(domain.getBookingTime()).isEqualTo("14:00");
            assertThat(domain.getStatus()).isEqualTo("CONFIRMED");
        }

        @Test
        void toDomainReturnsNullForNullEntity() {
            assertThat(mapper.toDomain(null)).isNull();
        }

        @Test
        void toEntityIgnoresIdField() {
            var domain = new BookingHistory();
            domain.setBookingId(UUID.randomUUID());
            domain.setCustomerId(UUID.randomUUID());
            domain.setServiceType("Tosa");
            domain.setBookingDate("11/08/2026");
            domain.setBookingTime("09:00");
            domain.setStatus("PENDING");

            var entity = mapper.toEntity(domain);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getBookingId()).isEqualTo(domain.getBookingId());
            assertThat(entity.getCustomerId()).isEqualTo(domain.getCustomerId());
            assertThat(entity.getServiceType()).isEqualTo("Tosa");
            assertThat(entity.getBookingDate()).isEqualTo("11/08/2026");
            assertThat(entity.getBookingTime()).isEqualTo("09:00");
            assertThat(entity.getStatus()).isEqualTo("PENDING");
        }

        @Test
        void toEntityReturnsNullForNullDomain() {
            assertThat(mapper.toEntity(null)).isNull();
        }
    }

    @Nested
    class PurchaseHistoryMapperTest {

        private final PurchaseHistoryMapper mapper = Mappers.getMapper(PurchaseHistoryMapper.class);

        @Test
        void toDomainMapsAllFieldsIncludingItems() {
            var entity = new CustomerPurchaseHistory();
            entity.setId("mongo-id-2");
            entity.setOrderId(UUID.randomUUID());
            entity.setCustomerId(UUID.randomUUID());
            var items = List.of(new PurchaseHistoryItem(1L, "Racao", 2, 50.0));
            entity.setItems(items);
            entity.setTotalAmount(100.0);
            var paidAt = LocalDateTime.now();
            entity.setPaidAt(paidAt);

            var domain = mapper.toDomain(entity);

            assertThat(domain.getOrderId()).isEqualTo(entity.getOrderId());
            assertThat(domain.getCustomerId()).isEqualTo(entity.getCustomerId());
            assertThat(domain.getItems()).isEqualTo(items);
            assertThat(domain.getTotalAmount()).isEqualTo(100.0);
            assertThat(domain.getPaidAt()).isEqualTo(paidAt);
        }

        @Test
        void toDomainReturnsNullForNullEntity() {
            assertThat(mapper.toDomain(null)).isNull();
        }

        @Test
        void toEntityIgnoresIdField() {
            var domain = new PurchaseHistory();
            domain.setOrderId(UUID.randomUUID());
            domain.setCustomerId(UUID.randomUUID());
            var items = List.of(new PurchaseHistoryItem(2L, "Brinquedo", 1, 20.0));
            domain.setItems(items);
            domain.setTotalAmount(20.0);
            var paidAt = LocalDateTime.now();
            domain.setPaidAt(paidAt);

            var entity = mapper.toEntity(domain);

            assertThat(entity.getId()).isNull();
            assertThat(entity.getOrderId()).isEqualTo(domain.getOrderId());
            assertThat(entity.getCustomerId()).isEqualTo(domain.getCustomerId());
            assertThat(entity.getItems()).isEqualTo(items);
            assertThat(entity.getTotalAmount()).isEqualTo(20.0);
            assertThat(entity.getPaidAt()).isEqualTo(paidAt);
        }

        @Test
        void toEntityReturnsNullForNullDomain() {
            assertThat(mapper.toEntity(null)).isNull();
        }
    }
}
