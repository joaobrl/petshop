package com.petshop.order_service.core.domain;

import com.petshop.order_service.core.domain.enums.CartStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Cart {

    private UUID id;

    private UUID customerId;

    private List<CartItem> items = new ArrayList<>();

    private CartStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    public static Cart openFor(UUID customerId, int abandonedAfterHours) {
        var cart = new Cart();
        cart.id = UUID.randomUUID();
        cart.customerId = customerId;
        cart.items = new ArrayList<>();
        cart.status = CartStatus.OPEN;
        cart.createdAt = LocalDateTime.now();
        cart.renewExpiration(abandonedAfterHours);
        return cart;
    }

    public void renewExpiration(int abandonedAfterHours) {
        this.expiresAt = LocalDateTime.now().plusHours(abandonedAfterHours);
    }

    public Optional<CartItem> findItem(Long productId) {
        return items.stream().filter(item -> item.getProductId().equals(productId)).findFirst();
    }

    public void addOrIncrementItem(Long productId, String productName, Double unitPrice, int quantity) {
        findItem(productId).ifPresentOrElse(
                item -> item.setQuantity(item.getQuantity() + quantity),
                () -> items.add(new CartItem(productId, productName, quantity, unitPrice))
        );
    }

    public void removeItem(Long productId) {
        items.removeIf(item -> item.getProductId().equals(productId));
    }

    public void removeItems(Collection<Long> productIds) {
        items.removeIf(item -> productIds.contains(item.getProductId()));
    }

    public List<Long> productIds() {
        return items.stream().map(CartItem::getProductId).toList();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public double total() {
        return items.stream().mapToDouble(CartItem::subtotal).sum();
    }

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
