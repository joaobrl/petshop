package com.petshop.customermanagement.core.domain;

import com.petshop.customermanagement.core.domain.enums.PetType;
import com.petshop.customermanagement.core.domain.enums.SizeCategory;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre os modelos de domínio "planos" (só getters/setters + equals/hashCode
 * gerados pelo Lombok @Data) e os enums — sem regra de negócio própria, mas
 * ainda assim exercitados linha a linha para fechar a cobertura.
 */
class SimpleDomainModelsTest {

    @Test
    void bookingHistoryGettersSettersAndEquality() {
        var id = UUID.randomUUID();
        var customerId = UUID.randomUUID();

        var history = new BookingHistory();
        history.setBookingId(id);
        history.setCustomerId(customerId);
        history.setServiceType("Banho");
        history.setBookingDate("10/08/2026");
        history.setBookingTime("14:00");
        history.setStatus("CONFIRMED");

        assertThat(history.getBookingId()).isEqualTo(id);
        assertThat(history.getCustomerId()).isEqualTo(customerId);
        assertThat(history.getServiceType()).isEqualTo("Banho");
        assertThat(history.getBookingDate()).isEqualTo("10/08/2026");
        assertThat(history.getBookingTime()).isEqualTo("14:00");
        assertThat(history.getStatus()).isEqualTo("CONFIRMED");

        var same = new BookingHistory();
        same.setBookingId(id);
        same.setCustomerId(customerId);
        same.setServiceType("Banho");
        same.setBookingDate("10/08/2026");
        same.setBookingTime("14:00");
        same.setStatus("CONFIRMED");

        assertThat(history).isEqualTo(same);
        assertThat(history.hashCode()).isEqualTo(same.hashCode());
        assertThat(history).isNotEqualTo(null);
        assertThat(history).isNotEqualTo("other");
        assertThat(history.toString()).contains("Banho");
    }

    @Test
    void purchaseHistoryGettersSettersAndEquality() {
        var orderId = UUID.randomUUID();
        var customerId = UUID.randomUUID();
        var paidAt = LocalDateTime.now();
        var items = List.of(new PurchaseHistoryItem(1L, "Racao", 2, 50.0));

        var history = new PurchaseHistory();
        history.setOrderId(orderId);
        history.setCustomerId(customerId);
        history.setItems(items);
        history.setTotalAmount(100.0);
        history.setPaidAt(paidAt);

        assertThat(history.getOrderId()).isEqualTo(orderId);
        assertThat(history.getCustomerId()).isEqualTo(customerId);
        assertThat(history.getItems()).isEqualTo(items);
        assertThat(history.getTotalAmount()).isEqualTo(100.0);
        assertThat(history.getPaidAt()).isEqualTo(paidAt);
        assertThat(history.toString()).contains("100.0");
    }

    @Test
    void purchaseHistoryItemGettersSettersAndEquality() {
        var item1 = new PurchaseHistoryItem(1L, "Racao", 2, 50.0);
        var item2 = new PurchaseHistoryItem(1L, "Racao", 2, 50.0);
        var item3 = new PurchaseHistoryItem(2L, "Brinquedo", 1, 20.0);

        assertThat(item1).isEqualTo(item2);
        assertThat(item1.hashCode()).isEqualTo(item2.hashCode());
        assertThat(item1).isNotEqualTo(item3);

        var emptyItem = new PurchaseHistoryItem();
        emptyItem.setProductId(1L);
        emptyItem.setProductName("Racao");
        emptyItem.setQuantity(2);
        emptyItem.setUnitPrice(50.0);

        assertThat(emptyItem).isEqualTo(item1);
    }

    @Test
    void petTypeHasExpectedValues() {
        assertThat(PetType.values()).containsExactly(PetType.DOG, PetType.CAT);
        assertThat(PetType.valueOf("DOG")).isEqualTo(PetType.DOG);
        assertThat(PetType.valueOf("CAT")).isEqualTo(PetType.CAT);
    }

    @Test
    void sizeCategoryHasExpectedValues() {
        assertThat(SizeCategory.values()).containsExactly(SizeCategory.SMALL, SizeCategory.MEDIUM, SizeCategory.LARGE);
        assertThat(SizeCategory.valueOf("SMALL")).isEqualTo(SizeCategory.SMALL);
        assertThat(SizeCategory.valueOf("MEDIUM")).isEqualTo(SizeCategory.MEDIUM);
        assertThat(SizeCategory.valueOf("LARGE")).isEqualTo(SizeCategory.LARGE);
    }
}
