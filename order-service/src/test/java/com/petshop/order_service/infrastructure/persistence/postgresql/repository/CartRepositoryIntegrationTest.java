package com.petshop.order_service.infrastructure.persistence.postgresql.repository;

import com.petshop.order_service.core.domain.enums.CartStatus;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.CartEntity;
import com.petshop.order_service.infrastructure.persistence.postgresql.entity.CartItemEmbeddable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sobe um H2 em memória (ver src/test/resources/application.yml) — não
 * precisa de Docker nem de um Postgres real.
 */
@DataJpaTest
class CartRepositoryIntegrationTest {

    @Autowired
    private CartRepository repository;

    private CartEntity openCart(UUID customerId, LocalDateTime expiresAt) {
        var item = new CartItemEmbeddable(1L, "Racao", 2, 10.0, true);
        var cart = new CartEntity();
        cart.setId(UUID.randomUUID());
        cart.setCustomerId(customerId);
        cart.setItems(List.of(item));
        cart.setStatus(CartStatus.OPEN);
        cart.setCreatedAt(LocalDateTime.now());
        cart.setExpiresAt(expiresAt);
        return cart;
    }

    @Test
    void savesAndFindsCartById() {
        var cart = openCart(UUID.randomUUID(), LocalDateTime.now().plusHours(1));

        var saved = repository.save(cart);
        var found = repository.findById(saved.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getItems()).hasSize(1);
        assertThat(found.get().getItems().get(0).getProductName()).isEqualTo("Racao");
    }

    @Test
    void findByIdReturnsEmptyForUnknownId() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findByCustomerIdAndStatusReturnsOnlyMatchingOpenCart() {
        var customerId = UUID.randomUUID();
        var openCart = openCart(customerId, LocalDateTime.now().plusHours(1));
        repository.save(openCart);

        var checkedOutCart = openCart(customerId, LocalDateTime.now().plusHours(1));
        checkedOutCart.setStatus(CartStatus.CHECKED_OUT);
        repository.save(checkedOutCart);

        var found = repository.findByCustomerIdAndStatus(customerId, CartStatus.OPEN);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(openCart.getId());
    }

    @Test
    void findByCustomerIdAndStatusReturnsEmptyWhenNoneMatch() {
        assertThat(repository.findByCustomerIdAndStatus(UUID.randomUUID(), CartStatus.OPEN)).isEmpty();
    }

    @Test
    void findByStatusAndExpiresAtBeforeReturnsOnlyExpiredOpenCarts() {
        var expired = openCart(UUID.randomUUID(), LocalDateTime.now().minusHours(1));
        repository.save(expired);

        var stillValid = openCart(UUID.randomUUID(), LocalDateTime.now().plusHours(1));
        repository.save(stillValid);

        var result = repository.findByStatusAndExpiresAtBefore(CartStatus.OPEN, LocalDateTime.now());

        assertThat(result).extracting(CartEntity::getId).contains(expired.getId()).doesNotContain(stillValid.getId());
    }

    @Test
    void findByStatusAndExpiresAtBeforeReturnsEmptyWhenNoneExpired() {
        var stillValid = openCart(UUID.randomUUID(), LocalDateTime.now().plusHours(1));
        repository.save(stillValid);

        assertThat(repository.findByStatusAndExpiresAtBefore(CartStatus.OPEN, LocalDateTime.now())).isEmpty();
    }
}
